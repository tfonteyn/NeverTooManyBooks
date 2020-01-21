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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searches.SiteList;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminActivity;
import com.hardbacknutter.nevertoomanybooks.settings.SearchAdminModel;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

public abstract class BookSearchBaseFragment
        extends Fragment {

    /** Log tag. */
    private static final String TAG = "BookSearchBaseFrag";
    DAO mDb;
    SearchCoordinator mSearchCoordinator;

    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** the ViewModel. */
    private ResultDataModel mResultDataModel;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Activity scope!
        //noinspection ConstantConditions
        mSearchCoordinator = new ViewModelProvider(getActivity()).get(SearchCoordinator.class);
        //noinspection ConstantConditions
        mSearchCoordinator.init(getContext(), requireArguments());
        mSearchCoordinator.getSearchCoordinatorProgressMessage()
                          .observe(getViewLifecycleOwner(), this::onSearchProgress);
        mSearchCoordinator.getSearchCoordinatorFinishedMessage()
                          .observe(getViewLifecycleOwner(), this::onSearchFinished);

        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog != null) {
            // reconnect after a fragment restart
            mProgressDialog.setCancellable(mSearchCoordinator);
        }

        // Warn the user, but don't abort.
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_network_no_connection, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        Resources r = getResources();
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 r.getInteger(R.integer.MENU_ORDER_SEARCH_SITES),
                 R.string.lbl_websites)
            .setIcon(R.drawable.ic_find_in_page)
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
                        .putExtra(SearchAdminModel.BKEY_LIST_TYPE,
                                  (Parcelable) SiteList.Type.Data)
                        .putExtra(SiteList.Type.Data.getBundleKey(),
                                  mSearchCoordinator.getSiteList());
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    abstract void onSearchResults(@NonNull Bundle bookData);

    private void onSearchFinished(@NonNull final TaskListener.FinishMessage<Bundle> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        if (message.status == TaskListener.TaskStatus.Cancelled) {
            onSearchCancelled();
            return;
        }

        String searchErrors = message.result.getString(SearchCoordinator.BKEY_SEARCH_ERROR);
        if (searchErrors != null) {
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.title_search_failed)
                    .setMessage(searchErrors)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
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

    private void onSearchProgress(@NonNull final TaskListener.ProgressMessage message) {
        if (mProgressDialog != null) {
            mProgressDialog.onProgress(message);
        }
    }

    @CallSuper
    void onSearchCancelled() {
        //noinspection ConstantConditions
        Snackbar.make(getView(), R.string.progress_end_cancelled, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Clear the SearchCoordinator search criteria.
     * <p>
     * Override to clear the input fields for the former.
     */
    @CallSuper
    void clearPreviousSearchCriteria() {
        mSearchCoordinator.clearSearchText();
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * This is final; override {@link #onSearch()}.
     */
    final void startSearch() {
        // check if we have an active search, if so, quit silently.
        if (mSearchCoordinator.isSearchActive()) {
            return;
        }

        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_network_no_connection, Snackbar.LENGTH_LONG)
                    .show();
            return;
        }

        // Start the lookup in a background search task.
        if (onSearch()) {
            // we started at least one search.
            mProgressDialog = ProgressDialogFragment
                    .newInstance(R.string.progress_msg_searching, true, false, 0);
            mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);
            mProgressDialog.setCancellable(mSearchCoordinator);
        } else {
            //TODO: not the best error message, but it will do for now.
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_search_failed_network, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * Override to customize which search function is called.
     *
     * @return {@code true} if a search was started
     */
    protected boolean onSearch() {
        //noinspection ConstantConditions
        return mSearchCoordinator.search(getContext());
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
                    SiteList siteList = data.getParcelableExtra(SiteList.Type.Data.getBundleKey());
                    if (siteList != null) {
                        mSearchCoordinator.setSiteList(siteList);
                    }
                }
                break;
            }
            case UniqueId.REQ_BOOK_EDIT: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mResultDataModel.putResultData(data);
                }
                break;
            }
//            case UniqueId.REQ_NAV_PANEL_SETTINGS: {
//                mSearchCoordinator.setSiteList(sites);
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
