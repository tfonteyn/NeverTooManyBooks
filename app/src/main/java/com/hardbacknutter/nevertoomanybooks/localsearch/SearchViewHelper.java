/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.localsearch;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.text.Editable;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.search.SearchView;

import java.util.ArrayList;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.util.insets.InsetsListenerBuilder;

/**
 * github #107 => https://issuetracker.google.com/issues/135594222
 *
 * 2025-02-22 Reminder: the old provider-based search is still active,
 * but no longer hooked up to the menus. It could all be deleted but it's
 * kept for now as there might be an alternative use for it.
 */
public class SearchViewHelper {
    private final ArrayList<SearchAdapter.SearchResult> searchResults = new ArrayList<>();
    @NonNull
    private final SearchView searchView;
    @NonNull
    private final Consumer<Long> displayBook;
    @NonNull
    private final Consumer<String> displayQuery;

    private final SearchAdapter searchAdapter;

    /**
     * Constructor.
     *
     * @param searchView   to handle
     * @param resultsView  to display the results in
     * @param displayBook  listener which will receive a book id
     *                     when the user taps on a search-result.
     * @param displayQuery listener which will receive the text query
     *                     when the user submits it
     */
    public SearchViewHelper(@NonNull final SearchView searchView,
                            @NonNull final RecyclerView resultsView,
                            @NonNull final Consumer<Long> displayBook,
                            @NonNull final Consumer<String> displayQuery) {
        this.searchView = searchView;
        this.displayBook = displayBook;
        this.displayQuery = displayQuery;
        InsetsListenerBuilder.apply(resultsView);

        searchView.inflateMenu(R.menu.search_view);
        searchView.setOnMenuItemClickListener(this::onMenuItemClick);

        searchAdapter = new SearchAdapter(this.searchView.getContext(), searchResults,
                                          this::onResultSelected);
        resultsView.setAdapter(searchAdapter);

        this.searchView.addTransitionListener(this::onStateChanged);
        this.searchView.getEditText().addTextChangedListener(
                (ExtTextWatcher) this::fetchResults);
    }

    /**
     * Show the view.
     *
     * @param initialQuery (optional) to populate the search field
     */
    public void show(@Nullable final CharSequence initialQuery) {
        final EditText editText = searchView.getEditText();
        editText.setText(initialQuery);
        if (initialQuery != null) {
            editText.setSelection(initialQuery.length());
        }
        searchView.show();
    }

    /**
     * Reset/clear the internal state when the view is hidden.
     */
    @SuppressWarnings("CheckStyle")
    private void onStateChanged(@NonNull final SearchView sv,
                                @NonNull final SearchView.TransitionState previousState,
                                @NonNull final SearchView.TransitionState newState) {
        if (newState == SearchView.TransitionState.HIDDEN) {
            this.searchView.getEditText().setText(null);
            searchResults.clear();
        }
    }

    private boolean onMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_SELECT) {
            this.searchView.hide();

            final String query = searchView.getText().toString();
            if (!query.isEmpty()) {
                displayQuery.accept(query);
            }
            return true;
        }
        return false;
    }

    private void onResultSelected(final long id) {
        this.searchView.hide();
        displayBook.accept(id);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void fetchResults(@NonNull final Editable s) {
        searchResults.clear();
        final String query = s.toString();
        if (!query.isEmpty()) {
            try (Cursor cursor = ServiceLocator.getInstance().getFtsDao()
                                               .querySearchSuggestions(query)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        searchResults.add(new SearchAdapter.SearchResult(
                                cursor.getLong(0),
                                cursor.getString(1),
                                cursor.getString(2)));
                    }
                }
            }
        }
        searchAdapter.notifyDataSetChanged();
    }
}
