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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminModel;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookSearchBaseModel;

public abstract class BookSearchBaseFragment
        extends Fragment {

    private static final String TAG = "BookSearchBaseFrag";

    /** hosting activity. */
    FragmentActivity mHostActivity;

    /** the ViewModel. */
    BookSearchBaseModel mBookSearchBaseModel;

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mHostActivity = (FragmentActivity) context;
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mBookSearchBaseModel = new ViewModelProvider(getActivity()).get(BookSearchBaseModel.class);
        //noinspection ConstantConditions
        mBookSearchBaseModel.init(getContext(), requireArguments(),
                                  ((BookSearchActivity) mHostActivity).getTaskManager());
        mBookSearchBaseModel.getSearchCancelled().observe(getViewLifecycleOwner(),
                                                          this::onSearchCancelled);
        mBookSearchBaseModel.getSearchResults().observe(getViewLifecycleOwner(), bookData -> {
            if (!bookData.isEmpty()) {
                onSearchResults(bookData);
            } else {
                //noinspection ConstantConditions
                UserMessage.show(getView(), R.string.warning_no_matching_book_found);
            }
        });

        // Warn the user, but don't abort.
        if (NetworkUtils.networkUnavailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_network_no_connection);
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "ENTER|onResume");
        }
        super.onResume();
        if (getActivity() instanceof BaseActivity) {
            BaseActivity activity = (BaseActivity) getActivity();
            if (activity.isGoingToRecreate()) {
                return;
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACK) {
            Log.d(TAG, "EXIT|onResume");
        }
    }

    /**
     * Child classes must call {@code setHasOptionsMenu(true)} from their {@link #onCreate}
     * to enable the option menu.
     * <br><br>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 MenuHandler.ORDER_SEARCH_SITES, R.string.lbl_websites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminModel.BKEY_TABS_TO_SHOW, SearchAdminModel.TAB_BOOKS)
                        .putExtra(SearchSites.BKEY_SEARCH_SITES_BOOKS,
                                  mBookSearchBaseModel.getSearchSites());
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    abstract void onSearchResults(@NonNull final Bundle bookData);

    void onSearchCancelled(final boolean isCancelled) {
        if (isCancelled) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.progress_end_cancelled);
        }
    }

    @CallSuper
    void clearPreviousSearchCriteria() {
        mBookSearchBaseModel.clearSearchText();
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * The results will arrive in
     * {@link SearchCoordinator.OnSearchFinishedListener#onSearchFinished(boolean, Bundle)}
     *
     * This is final; instead override {@link #customizeSearch()} if needed.
     */
    final void startSearch() {
        // check if we have an active search, if so, quit silently.
        if (mBookSearchBaseModel.isSearchActive()) {
            return;
        }

        // cannot search without network
        if (NetworkUtils.networkUnavailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_network_no_connection);
            return;
        }

        try {
            // Start the lookup in a background search task.
            if (customizeSearch()) {
                // we started at least one search.
                mBookSearchBaseModel.sendHeaderUpdate(getString(R.string.progress_msg_searching));
                mBookSearchBaseModel.setSearchActive(true);
            }
            return;

        } catch (@NonNull final RuntimeException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_search_failed);
        }

        Intent resultData = mBookSearchBaseModel.getActivityResultData();
        if (resultData.getExtras() != null) {
            mHostActivity.setResult(Activity.RESULT_OK, resultData);
        }
        mHostActivity.finish();
    }

    /**
     * Override to customize which search function is called.
     */
    protected boolean customizeSearch() {
        return mBookSearchBaseModel.startSearch();
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case UniqueId.REQ_PREFERRED_SEARCH_SITES: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<Site> sites = data.getParcelableArrayListExtra(
                            SearchSites.BKEY_SEARCH_SITES_BOOKS);
                    if (sites != null) {
                        mBookSearchBaseModel.setSearchSites(sites);
                    }
                    // Make sure that the ASIN option (Amazon) is (not) offered.
                    //noinspection ConstantConditions
                    getActivity().invalidateOptionsMenu();
                }
                break;
            }
            case UniqueId.REQ_BOOK_EDIT: {
                if (resultCode == Activity.RESULT_OK) {
                    // Created a book? Save the intent
                    if (data != null) {
                        mBookSearchBaseModel.setLastBookData(data);
                    }
                }
                break;
            }
//            case UniqueId.REQ_NAV_PANEL_SETTINGS: {
//
//                mBookSearchBaseModel.setSearchSites(sites);
//            }

            default: {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "onActivityResult|NOT HANDLED"
                               + "|requestCode=" + requestCode
                               + "|resultCode=" + resultCode, new Throwable());
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }
}
