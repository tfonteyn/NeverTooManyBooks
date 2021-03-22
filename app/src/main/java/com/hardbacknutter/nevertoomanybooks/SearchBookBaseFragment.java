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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookFromBundleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchSitesSingleListContract;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultIntentOwner;

public abstract class SearchBookBaseFragment
        extends BaseFragment {

    private final ActivityResultLauncher<Bundle> mEditBookFoundLauncher = registerForActivityResult(
            new EditBookFromBundleContract(), this::onBookEditingDone);
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK,
                                            getResultIntentOwner().getResultIntent());
                    getActivity().finish();
                }
            };
    SearchCoordinator mCoordinator;
    private final ActivityResultLauncher<ArrayList<Site>> mEditSitesLauncher =
            registerForActivityResult(new SearchSitesSingleListContract(),
                                      sites -> {
                                          if (sites != null) {
                                              // no changes committed, temporary usage only
                                              mCoordinator.setSiteList(sites);
                                          }
                                      });
    @Nullable
    private ProgressDialogFragment mProgressDialog;

    @NonNull
    public abstract ResultIntentOwner getResultIntentOwner();

    @CallSuper
    void onBookEditingDone(@Nullable final Bundle data) {
        if (data != null) {
            getResultIntentOwner().getResultIntent().putExtras(data);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        // Activity scope!
        mCoordinator = new ViewModelProvider(getActivity()).get(SearchCoordinator.class);
        //noinspection ConstantConditions
        mCoordinator.init(getContext(), requireArguments());
        mCoordinator.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        // Handle both Success and Failed searches
        mCoordinator.onSearchFinished().observe(getViewLifecycleOwner(), this::onSearchFinished);
        mCoordinator.onSearchCancelled().observe(getViewLifecycleOwner(), message -> {
            closeProgressDialog();
            onSearchCancelled();
        });

        // Warn the user, but don't abort.
        if (!NetworkUtils.isNetworkAvailable()) {
            Snackbar.make(view, R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        final Resources r = getResources();
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                 R.string.lbl_websites)
            .setIcon(R.drawable.ic_baseline_find_in_page_24)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_PREFS_SEARCH_SITES) {
            mEditSitesLauncher.launch(mCoordinator.getSiteList());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onSearchFinished(@NonNull final FinishedMessage<Bundle> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            final String searchErrors = message.result
                    .getString(SearchCoordinator.BKEY_SEARCH_ERROR);
            if (searchErrors != null) {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.warning_search_failed)
                        .setMessage(searchErrors)
                        .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                        .create()
                        .show();

            } else if (!message.result.isEmpty()) {
                onSearchResults(message.result);

            } else {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.warning_no_matching_book_found,
                              Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @CallSuper
    void onSearchCancelled() {
        //noinspection ConstantConditions
        Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            final FragmentManager fm = getChildFragmentManager();
            // get dialog after a fragment restart
            mProgressDialog = (ProgressDialogFragment)
                    fm.findFragmentByTag(ProgressDialogFragment.TAG);
            // not found? create it
            if (mProgressDialog == null) {
                mProgressDialog = ProgressDialogFragment.newInstance(
                        getString(R.string.progress_msg_searching), true, false);
                mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            }

            // hook the task up.
            mProgressDialog.setCanceller(mCoordinator);
        }

        mProgressDialog.onProgress(message);
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }


    /**
     * Clear the SearchCoordinator search criteria.
     * <p>
     * Override to clear the input fields for the former.
     * Make sure to always call the super.
     */
    @CallSuper
    void onClearSearchCriteria() {
        mCoordinator.clearSearchCriteria();
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * This is final; override {@link #onPreSearch()} and {@link #onSearch()} as needed.
     * The results come in {@link #onSearchResults(Bundle)}.
     */
    final void startSearch() {
        // check if we have an active search, if so, quit silently.
        if (mCoordinator.isSearchActive()) {
            return;
        }

        // any implementation specific reasons not to start searching ?
        if (!onPreSearch()) {
            return;
        }

        if (!NetworkUtils.isNetworkAvailable()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_network_please_connect,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        // Start the lookup in a background search task.
        if (!onSearch()) {
            //TODO: not the best error message, but it will do for now.
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_search_failed_network,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Override to prevent or allow a search to start.
     * The default implementation allows a search to start.
     *
     * @return {@code true} if a search is allowed
     */
    boolean onPreSearch() {
        return true;
    }

    /**
     * Override to customize which search function is called.
     * The default implementation starts the generic {@link SearchCoordinator#search()}.
     *
     * @return {@code true} if a search was started
     */
    boolean onSearch() {
        return mCoordinator.search();
    }

    /**
     * Process the search results.
     * The default implementation starts the book-edit activity.
     *
     * @param bookData Bundle with the results
     */
    void onSearchResults(@NonNull final Bundle bookData) {
        mEditBookFoundLauncher.launch(bookData);
        onClearSearchCriteria();
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @NonNull final CharSequence error) {
        til.setError(error);
        til.postDelayed(() -> til.setError(null), BaseActivity.ERROR_DELAY_MS);
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @StringRes final int error) {
        til.setError(getString(error));
        til.postDelayed(() -> til.setError(null), BaseActivity.ERROR_DELAY_MS);
    }
}
