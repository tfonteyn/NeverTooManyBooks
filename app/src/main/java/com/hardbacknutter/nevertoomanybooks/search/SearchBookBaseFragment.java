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
package com.hardbacknutter.nevertoomanybooks.search;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.EmptySuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.widgets.ExtTextWatcher;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchCriteria;
import com.hardbacknutter.nevertoomanybooks.searchengines.BookSearchResult;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.settings.searchsites.SearchSitesSingleListContract;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;

public abstract class SearchBookBaseFragment
        extends BaseFragment {

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, createResultIntent());
                    getActivity().finish();
                }
            };
    ActivityResultLauncher<EditBookContract.Input> editBookLauncher;
    SearchCoordinator coordinator;

    private ActivityResultLauncher<List<Site>> editSitesLauncher;
    @Nullable
    private ProgressDelegate progressDelegate;

    protected void explainSitesSupport(@Nullable final List<Site> sites) {
        // override as needed, e.g. SearchBookByTextFragment
    }

    @NonNull
    abstract Intent createResultIntent();

    /**
     * The user finished editing a book.
     *
     * @param data from the edit
     */
    abstract void onBookEditingDone(@NonNull EditBookOutput data);

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createActivityLaunchers();

        //noinspection DataFlowIssue
        coordinator = new ViewModelProvider(getActivity()).get(SearchCoordinator.class);
        //noinspection DataFlowIssue
        coordinator.init(getContext(), requireArguments());
    }

    private void createActivityLaunchers() {
        editBookLauncher = registerForActivityResult(
                new EditBookContract(),
                o -> o.ifPresent(this::onBookEditingDone));

        editSitesLauncher = registerForActivityResult(
                new SearchSitesSingleListContract(),
                o -> o.ifPresent(sites -> {
                    coordinator.setSiteList(sites);
                    explainSitesSupport(sites);
                }));
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        coordinator.onSearchProgress().observe(getViewLifecycleOwner(), this::onProgress);
        // Handle both Success and Failed searches
        coordinator.onSearchFinished().observe(getViewLifecycleOwner(), this::onSearchFinished);
        coordinator.onSearchCancelled().observe(getViewLifecycleOwner(), this::onSearchCancelled);

        // Warn the user, but don't abort.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            Snackbar.make(view, R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.process(progress -> {
            if (progressDelegate == null) {
                //noinspection DataFlowIssue
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.progress_msg_searching)
                        .setIndeterminate(true)
                        .setOnCancelListener(v -> coordinator.cancelTask(progress.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(progress);
        });
    }

    void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * The results come back in {@link #onSearchResults(Book)}.
     * <p>
     * This is final; override
     * {@link #onPreSearch(BookSearchCriteria)} and
     * {@link #onSearch(BookSearchCriteria)} as needed.
     *
     * @param criteria to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     */
    final int startSearch(@NonNull final BookSearchCriteria criteria) {
        // check if we have an active search, if so, quit silently.
        if (coordinator.isSearchActive()) {
            return 0;
        }

        // any implementation specific reasons not to start searching ?
        if (!onPreSearch(criteria)) {
            return 0;
        }

        // Warn the user, AND abort.
        if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
            return 0;
        }

        // Start the lookup in a background search task.
        final int searchId = onSearch(criteria);
        if (searchId == 0) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.error_search_could_not_be_started,
                          Snackbar.LENGTH_LONG).show();
        }
        return searchId;
    }

    /**
     * Override to prevent or allow a search to start.
     * The default implementation allows a search to start.
     *
     * @param criteria to search for
     *
     * @return {@code true} if a search is allowed
     */
    boolean onPreSearch(@NonNull final BookSearchCriteria criteria) {
        return true;
    }

    /**
     * Override to customize which search function is called.
     * The default implementation starts the generic
     * {@link SearchCoordinator#search(BookSearchCriteria)}.
     *
     * @param criteria to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     */
    int onSearch(@NonNull final BookSearchCriteria criteria) {
        return coordinator.search(criteria);
    }

    abstract void onSearchCancelled(@NonNull LiveDataEvent<Boolean> message);

    /**
     * FIXME: make overriding foolproof
     * When overriding this method, do <strong>NOT</strong> call this super,
     * but you <strong>MUST CALL {@link #closeProgressDialog()}
     * and {@link #onClearSearchCriteria}</strong>.
     *
     * @param message with results
     */
    @EmptySuper
    void onSearchFinished(@NonNull final LiveDataEvent<Boolean> message) {
        closeProgressDialog();
        message.process(trigger -> {
            @Nullable
            final BookSearchResult result = coordinator.pollFinishedQueue();
            if (result == null) {
                return;
            }
            final Book book = result.getBook();
            final String searchErrors = result.getSearchErrors();
            final boolean hasData = !book.isEmpty();

            if (searchErrors != null && !searchErrors.isEmpty()) {
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.warning_24px)
                        .setTitle(hasData ? R.string.warning_book_not_always_found
                                          : R.string.warning_book_not_found)
                        .setMessage(searchErrors)
                        .setPositiveButton(R.string.ok, (d, w) -> {
                            d.dismiss();
                            if (hasData) {
                                onSearchResults(book);
                                onClearSearchCriteria();
                            }
                        })
                        .create()
                        .show();

            } else if (hasData) {
                onSearchResults(book);
                onClearSearchCriteria();

            } else {
                //noinspection DataFlowIssue
                Snackbar.make(getView(), R.string.warning_no_matching_book_found,
                              Snackbar.LENGTH_LONG).show();
            }

            coordinator.retriggerSearchFinished();
        });
    }

    // Don't allow child classes to override.

    /**
     * Clear the search criteria and the input fields.
     */
    @CallSuper
    abstract void onClearSearchCriteria();

    /**
     * Process the search results.
     *
     * @param book results of the search
     */
    abstract void onSearchResults(@NonNull Book book);

    /**
     * Add the needed listeners to automatically remove any error text from
     * a {@link TextInputLayout} when the user changes the content.
     *
     * @param editText inner text edit view
     * @param til      outer layout view
     */
    protected void autoRemoveError(@NonNull final EditText editText,
                                   @NonNull final TextInputLayout til) {
        editText.addTextChangedListener((ExtTextWatcher) s -> til.setError(null));
        // REMINDER: this overrides the default listener which would show/remove the "end_icon"
        // This is in fact what we want - finally... and android "issue" we like.
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                til.setError(null);
            }
        });
    }

    class SearchSitesToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            final Resources r = getResources();
            menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                     r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                     R.string.lbl_websites)
                .setIcon(R.drawable.find_in_page_24px)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_PREFS_SEARCH_SITES) {
                editSitesLauncher.launch(coordinator.getSiteList());
                return true;
            }
            return false;
        }
    }

}
