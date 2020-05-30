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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
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

        // Activity scope!
        //noinspection ConstantConditions
        mSearchCoordinator = new ViewModelProvider(getActivity()).get(SearchCoordinator.class);
        //noinspection ConstantConditions
        mSearchCoordinator.init(getContext(), requireArguments());
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSearchCoordinator.onProgress()
                          .observe(getViewLifecycleOwner(), this::onSearchProgress);
        mSearchCoordinator.onOneBookDone()
                          .observe(getViewLifecycleOwner(), this::onSearchFinished);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        // Warn the user, but don't abort.
        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            Snackbar.make(view, R.string.error_network_no_connection,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        final Resources r = getResources();
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
            case R.id.MENU_PREFS_SEARCH_SITES: {
                final Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminModel.BKEY_LIST_TYPE,
                                  (Parcelable) SiteList.Type.Data)
                        .putExtra(SiteList.Type.Data.getBundleKey(),
                                  mSearchCoordinator.getSiteList());
                startActivityForResult(intent, RequestCode.PREFERRED_SEARCH_SITES);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onSearchFinished(@NonNull final TaskListener.FinishMessage<Bundle> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        if (message.status == TaskListener.TaskStatus.Cancelled) {
            onSearchCancelled();
            return;
        }

        // sanity check
        Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);

        final String searchErrors = message.result.getString(SearchCoordinator.BKEY_SEARCH_ERROR);
        if (searchErrors != null) {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
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

    private void onSearchProgress(@NonNull final TaskListener.ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog();
        }
        mProgressDialog.onProgress(message);
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog() {
        FragmentManager fm = getChildFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        // not found? create it
        if (dialog == null) {
            dialog = ProgressDialogFragment
                    .newInstance(R.string.progress_msg_searching, true, false, 0);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mSearchCoordinator);

        return dialog;
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
     * Make sure to always call the super.
     */
    @CallSuper
    void onClearPreviousSearchCriteria() {
        mSearchCoordinator.clearSearchText();
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * This is final; override {@link #onPreSearch()} and {@link #onSearch()} as needed.
     * The results come in {@link #onSearchResults(Bundle)}.
     */
    final void startSearch() {
        // check if we have an active search, if so, quit silently.
        if (mSearchCoordinator.isSearchActive()) {
            return;
        }

        // any implementation specific reasons not to start searching ?
        if (!onPreSearch()) {
            return;
        }

        //noinspection ConstantConditions
        if (!NetworkUtils.isNetworkAvailable(getContext())) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_network_no_connection,
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
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean onPreSearch() {
        return true;
    }

    /**
     * Override to customize which search function is called.
     * The default implementation starts the generic {@link SearchCoordinator#search(Context)}.
     *
     * @return {@code true} if a search was started
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean onSearch() {
        //noinspection ConstantConditions
        return mSearchCoordinator.search(getContext());
    }

    /**
     * Process the search results.
     * The default implementation starts the book-edit activity.
     *
     * @param bookData a Bundle with the results
     */
    void onSearchResults(@NonNull Bundle bookData) {
        final Intent intent = new Intent(getContext(), EditBookActivity.class)
                .putExtra(Book.BKEY_BOOK_DATA, bookData);
        startActivityForResult(intent, RequestCode.BOOK_EDIT);
        onClearPreviousSearchCriteria();
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @NonNull final CharSequence error) {
        til.setError(error);
        new Handler().postDelayed(() -> til.setError(null), BaseActivity.ERROR_DELAY_MS);
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
            case RequestCode.PREFERRED_SEARCH_SITES: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    SiteList siteList = data.getParcelableExtra(SiteList.Type.Data.getBundleKey());
                    if (siteList != null) {
                        mSearchCoordinator.setSiteList(siteList);
                    }
                }
                break;
            }
            case RequestCode.BOOK_EDIT: {
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
