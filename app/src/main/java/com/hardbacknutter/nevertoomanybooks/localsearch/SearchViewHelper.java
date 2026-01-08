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

package com.hardbacknutter.nevertoomanybooks.localsearch;

import android.annotation.SuppressLint;
import android.view.MenuItem;
import android.view.View;
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
import com.hardbacknutter.nevertoomanybooks.database.dao.FtsSearchResult;

/**
 * GitHub #107 => https://issuetracker.google.com/issues/135594222
 * <p>
 * 2025-02-22 Reminder: the old provider-based search is still active,
 * but no longer hooked up to the menus. It could all be deleted but it's
 * kept for now as there might be an alternative use for it.
 */
public class SearchViewHelper {
    private final ArrayList<FtsSearchResult> searchResults = new ArrayList<>();
    @NonNull
    private final SearchView searchView;
    @NonNull
    private final Consumer<Long> displayBook;
    @NonNull
    private final Consumer<String> displayQuery;

    private final SearchAdapter searchAdapter;

    private final TimerDelegate timerDelegate;
    private final ExtTextWatcher textWatcher;
    private final View.OnTouchListener touchListener;

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
    @SuppressLint("ClickableViewAccessibility")
    public SearchViewHelper(@NonNull final SearchView searchView,
                            @NonNull final RecyclerView resultsView,
                            @NonNull final Consumer<Long> displayBook,
                            @NonNull final Consumer<String> displayQuery) {
        this.searchView = searchView;
        this.displayBook = displayBook;
        this.displayQuery = displayQuery;

        // The callback comes from the timer thread, hence use "post"
        timerDelegate = new TimerDelegate(() -> this.searchView.post(this::fetchResults));

        // Detect when user touches something outside the EditText
        touchListener = (v, event) -> {
            timerDelegate.userIsActive(false);
            return false;
        };

        textWatcher = s -> timerDelegate.userIsActive(true);

        searchAdapter = new SearchAdapter(this.searchView.getContext(), searchResults,
                                          this::onResultSelected);
        resultsView.setAdapter(searchAdapter);

        this.searchView.inflateMenu(R.menu.search_view);
        this.searchView.setOnMenuItemClickListener(this::onMenuItemClick);
        this.searchView.addTransitionListener(this::onStateChanged);
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

    /**
     * Reset/clear the internal state when the view is hidden.
     */
    @SuppressLint("ClickableViewAccessibility")
    @SuppressWarnings("CheckStyle")
    private void onStateChanged(@NonNull final SearchView sv,
                                @NonNull final SearchView.TransitionState previousState,
                                @NonNull final SearchView.TransitionState newState) {
        switch (newState) {
            case SHOWN: {
                this.searchView.getEditText().addTextChangedListener(textWatcher);
                this.searchView.setOnTouchListener(touchListener);
                break;
            }
            case HIDDEN: {
                timerDelegate.stopIdleTimer();
                final EditText editText = this.searchView.getEditText();
                editText.removeTextChangedListener(textWatcher);
                editText.setText(null);
                this.searchView.setOnTouchListener(null);
                searchResults.clear();
                break;
            }
        }
    }

    /**
     * The user tapped a single book result.
     *
     * @param id of the book
     */
    private void onResultSelected(final long id) {
        this.searchView.hide();
        displayBook.accept(id);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void fetchResults() {
        searchResults.clear();
        final String query = this.searchView.getEditText().getText().toString();
        if (!query.isEmpty()) {
            searchResults.addAll(ServiceLocator.getInstance().getFtsDao().search(query));
        }
        searchAdapter.notifyDataSetChanged();
    }
}
