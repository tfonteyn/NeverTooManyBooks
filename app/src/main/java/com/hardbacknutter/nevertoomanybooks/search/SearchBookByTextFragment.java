/*
 * @Copyright 2018-2026 HardBackNutter
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
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.core.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByTextBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.widgets.endicon.ExtClearTextEndIconDelegate;
import com.hardbacknutter.nevertoomanybooks.localsearch.LocalSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

public class SearchBookByTextFragment
        extends SearchBookBaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SearchBookByTextFragment";

    /** adapter for the AutoCompleteTextView. */
    private ExtArrayAdapter<String> authorAdapter;
    /** adapter for the AutoCompleteTextView. */
    private ExtArrayAdapter<String> seriesAdapter;
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
        vm.init(requireArguments());
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
        InsetsListenerBuilder.fragmentRootView(view);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_search_for_books);
        toolbar.addMenuProvider(new SearchSitesToolbarMenuProvider(), getViewLifecycleOwner());

        modelToView();

        autoRemoveError(vb.lblTitle, vb.title);
        ExtClearTextEndIconDelegate.attach(vb.lblTitle, null);

        autoRemoveError(vb.lblAuthor, vb.author);

        ExtClearTextEndIconDelegate.attach(vb.lblSeriesNum, null);

        // For small screens which bring up the IME text editor
        vb.publisher.setOnEditorActionListener(this::onEditorAction);

        populateAdapters();

        vb.btnSearch.setOnClickListener(v -> startSearch());
        explainSitesSupport();
    }

    private void autoRemoveError(@NonNull final TextInputLayout til,
                                 @NonNull final EditText editText) {
        // user type -> clear
        editText.addTextChangedListener((ExtTextWatcher) s -> {
            vb.lblTitle.setError(null);
            vb.lblAuthor.setError(null);
        });
        // focused -> clear
        // REMINDER: this overrides the default listener which would show/remove the "end_icon"
        // This is in fact what we want - finally... and android "issue" we like.
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                vb.lblTitle.setError(null);
                vb.lblAuthor.setError(null);
            }
        });
    }

    @Override
    protected void explainSitesSupport() {
        final Context context = getContext();

        final Set<SearchEngine.SearchBy> searchBy = Set.of(SearchEngine.SearchBy.Text);

        //noinspection DataFlowIssue
        final List<String> engines = coordinator
                .getSiteList()
                .stream()
                .filter(Site::isActive)
                .map(Site::getEngineId)
                .filter(engineId -> searchBy.stream().anyMatch(engineId::supports))
                .map(engineId -> engineId.getName(context))
                .collect(Collectors.toList());

        if (!engines.isEmpty()) {
            // Explicitly let the user known which sites will be searched.
            vb.btnSearch.setEnabled(true);
            final int textColor = AttrUtils
                    .getColorInt(context, com.google.android.material.R.attr.colorOnBackground);
            vb.txtLimitations.setTextColor(textColor);
            vb.txtLimitations.setText(getString(R.string.info_sites_active,
                                                String.join(", ", engines)));
            return;
        }

        // There are no sites which support searching by Text
        vb.btnSearch.setEnabled(false);
        // don't use android.R.attr.colorError which is API 29+ only
        //noinspection DataFlowIssue
        final int textColor = AttrUtils
                .getColorInt(context, androidx.appcompat.R.attr.colorError);
        vb.txtLimitations.setTextColor(textColor);
        vb.txtLimitations.setText(getString(R.string.warning_no_site_supports_this_method,
                                            // TODO: support RTL
                                            getString(R.string.lbl_author)
                                            + " / " + getString(R.string.lbl_title)));
        vb.txtLimitations.setVisibility(View.VISIBLE);
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

    void modelToView() {
        final BookSearchCriteria criteria = vm.getSearchCriteria();
        vb.title.setText(criteria.getTitle());
        vb.author.setText(criteria.getAuthor());
        vb.seriesTitle.setText(criteria.getSeries());
        vb.seriesNum.setText(criteria.getSeriesNr());
        vb.publisher.setText(criteria.getPublisher());
    }

    void viewToModel() {
        final BookSearchCriteria criteria = vm.getSearchCriteria();
        //noinspection DataFlowIssue
        criteria.setTitle(vb.title.getText().toString().strip());
        criteria.setAuthor(vb.author.getText().toString().strip());
        criteria.setSeries(vb.seriesTitle.getText().toString().strip());
        //noinspection DataFlowIssue
        criteria.setSeriesNr(vb.seriesNum.getText().toString().strip());
        criteria.setPublisher(vb.publisher.getText().toString().strip());
    }

    /**
     * Set up the adapters for the AutoCompleteTextView fields.
     */
    private void populateAdapters() {
        //noinspection DataFlowIssue
        final Locale userLocale = getContext().getResources().getConfiguration()
                                              .getLocales().get(0);

        authorAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getAuthorNames(userLocale));
        vb.author.setAdapter(authorAdapter);

        seriesAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getSeriesNames(userLocale));
        vb.seriesTitle.setAdapter(seriesAdapter);

        publisherAdapter = new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Diacritic, vm.getPublisherNames(userLocale));
        vb.publisher.setAdapter(publisherAdapter);
    }

    /**
     * Prepare the criteria object to use and start a search.
     * <p>
     * This method can interact with the user,
     * and can reject starting a search.
     */
    private void startSearch() {
        viewToModel();

        // check if we have an active search, if so, quit silently.
        if (isSearchActive()) {
            return;
        }

        final BookSearchCriteria criteria = vm.getSearchCriteria();

        final String authorSearchText = criteria.getAuthor();
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

        final String seriesSearchText = criteria.getSeries();
        if (!seriesSearchText.isEmpty()) {
            // Always add the current search text (if not already present)
            // to the list of recent searches.
            if (seriesAdapter.getPosition(seriesSearchText) < 0) {
                if (vm.addSeriesName(seriesSearchText)) {
                    // Add to adapter, in case search produces no results
                    seriesAdapter.add(seriesSearchText);
                }
            }
        }

        final String publisherSearchText = criteria.getPublisher();
        if (!publisherSearchText.isEmpty()) {
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
        final String titleSearchText = criteria.getTitle();
        if (titleSearchText.isEmpty()
            && authorSearchText.isEmpty()) {
            vb.lblTitle.setError(getString(R.string.warning_missing_title_or_author));
            return;
        }

        final int searchId = coordinator.search(criteria);
        if (searchId == 0) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_book_search_failed,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    void onSearchResults(@NonNull final BookSearchResult result) {
        final Book book = result.getBook();

        // If any of the below criteria fields are not present in the result,
        // we add them manually as the template for a new book.
        final BookSearchCriteria criteria = vm.getSearchCriteria();

        if (!book.contains(DBKey.TITLE)) {
            book.setTitle(criteria.getTitle());
        }

        final List<Author> authors = book.getAuthors();
        if (authors.isEmpty()) {
            // do NOT use {@code Book.BKEY_AUTHOR_LIST}, that's reserved for verified names.
            book.putString(LocalSearchCriteria.BKEY_SEARCH_TEXT_AUTHOR,
                           criteria.getAuthor());
        }

        final List<Series> series = book.getSeries();
        if (series.isEmpty()) {
            // do NOT use {@code Book.BKEY_SERIES_LIST}, that's reserved for verified names.
            book.putString(LocalSearchCriteria.BKEY_SEARCH_TEXT_SERIES,
                           criteria.getSeries());
            book.putString(DBKey.SERIES.BOOK_SERIES_NUMBER,
                           criteria.getSeriesNr());
        }

        final List<Publisher> publishers = book.getPublishers();
        if (publishers.isEmpty()) {
            // do NOT use {@code Book.BKEY_PUBLISHER_LIST}, that's reserved for verified names.
            book.putString(LocalSearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER,
                           criteria.getPublisher());
        }

        editBook(book, vm.getStyle());
    }

    @Override
    void onClearSearchCriteria() {
        vm.getSearchCriteria().reset();

        vb.title.setText("");
        vb.author.setText("");
        vb.seriesTitle.setText("");
        vb.seriesNum.setText("");
        vb.publisher.setText("");
    }

    /**
     * The user finished editing a book. Store results and refresh the dropdown lists.
     *
     * @param data from the edit
     */
    @Override
    void onBookEditingDone(@NonNull final EditBookOutput data) {
        onClearSearchCriteria();

        vm.onBookEditingDone(data);

        // refresh, we could have modified/created items while editing
        // (even when the edit was cancelled )
        populateAdapters();
    }
}
