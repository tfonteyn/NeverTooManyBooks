package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.Objects;

public abstract class BookSearchBaseFragment extends Fragment
        implements SearchManager.SearchManagerListener {

    /** optionally limit the sites to search on. By default uses {@link SearchSites.Site#SEARCH_ALL} */
    public static final String REQUEST_BKEY_SEARCH_SITES = "SearchSites";
    /** stores an active search id, or 0 when none active */
    public static final String BKEY_SEARCH_MANAGER_ID = "SearchManagerId";
    /** the last book data (intent) we got from a successful EditBook */
    public static final String BKEY_LAST_BOOK_INTENT = "LastBookIntent";

    protected BookSearchActivity mActivity;

    /** Database instance */
    protected CatalogueDBAdapter mDb;

    /** sites to search on. Can be overridden by the user (option menu) */
    protected int mSearchSites = SearchSites.Site.SEARCH_ALL;
    /** Objects managing current search. */
    protected long mSearchManagerId = 0;

    /** The last Intent returned as a result of creating a book. */
    @Nullable
    protected Intent mLastBookData = null;

    /**
     * Called to do initial creation of a fragment.  This is called after
     * {@link #onAttach(Activity)} and before
     * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     *
     * <p>Note that this can be called while the fragment's activity is
     * still in the process of being created.  As such, you can not rely
     * on things like the activity's content view hierarchy being initialized
     * at this point.  If you want to do work once the activity itself is
     * created, see {@link #onActivityCreated(Bundle)}.
     *
     * <p>Any restored child fragments will be created before the base
     * <code>Fragment.onCreate</code> method returns.</p>
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */
    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        // make sure {@link #onCreateOptionsMenu} is called
        this.setHasOptionsMenu(true);
        Tracker.exitOnCreate(this);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnActivityCreated(this, savedInstanceState);

        // cache to avoid multiple calls to requireActivity()
        mActivity = (BookSearchActivity) requireActivity();

        super.onActivityCreated(savedInstanceState);

        mDb = new CatalogueDBAdapter(mActivity);

        if (savedInstanceState != null) {
            mSearchManagerId = savedInstanceState.getLong(BKEY_SEARCH_MANAGER_ID);
            /* optional, use ALL if not there */
            mSearchSites = savedInstanceState.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.Site.SEARCH_ALL);
        } else {
            Bundle args = getArguments();
            //noinspection ConstantConditions
            mSearchManagerId = args.getLong(BKEY_SEARCH_MANAGER_ID);
            /* optional, use ALL if not there */
            mSearchSites = args.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.Site.SEARCH_ALL);
        }

        if ((mSearchSites & SearchSites.Site.SEARCH_LIBRARY_THING) != 0) {
            LibraryThingManager.showLtAlertIfNecessary(mActivity, false, "search");
        }

        boolean network_available = Utils.isNetworkAvailable(mActivity);
        if (!network_available) {
            StandardDialogs.showUserMessage(mActivity, R.string.error_no_internet_connection);
        }

        Tracker.exitOnActivityCreated(this);
    }

    /**
     * @param menu The options menu in which you place your items.
     *
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(final @NonNull Menu menu, final @NonNull MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES, 0, R.string.tab_lbl_search_sites)
                .setIcon(R.drawable.ic_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(this.requireContext(), SearchAdminActivity.class);
                intent.putExtra(SearchAdminActivity.REQUEST_BKEY_TAB, SearchAdminActivity.TAB_SEARCH_ORDER);
                startActivityForResult(intent, SearchAdminActivity.REQUEST_CODE); /* 1b923299-d966-4ed5-8230-c5a7c491053b */
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * (re)connect with the {@link SearchManager} by starting to listen to its messages
     */
    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch().addListener(mSearchManagerId, this, true);
        }
        Tracker.exitOnResume(this);
    }

    /**
     * Start the actual search with the {@link SearchManager} in the background.
     *
     * The results will arrive in {@link SearchManager.SearchManagerListener#onSearchFinished(boolean, Bundle)}
     *
     * @return true if search was started.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean startSearch(final @NonNull String authorSearchText,
                                  final @NonNull String titleSearchText,
                                  final @NonNull String isbnSearchText) {

        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "startSearch|isbn=" + isbnSearchText + "|author=" + authorSearchText + "|title=" + titleSearchText);
        }

        /* Get the book */
        try {
            // Start the lookup in a background search task.
            final SearchManager searchManager = new SearchManager(mActivity.getTaskManager(), this);
            mSearchManagerId = searchManager.getId();

            Tracker.handleEvent(this, Tracker.States.Running, "Created SearchManager=" + mSearchManagerId);

            mActivity.getTaskManager().sendHeaderTaskProgressMessage(getString(R.string.progress_msg_searching));
            // kick of the searches
            searchManager.search(mSearchSites, authorSearchText, titleSearchText, isbnSearchText, true);
            return true;

        } catch (Exception e) {
            Logger.error(e);
            StandardDialogs.showUserMessage(mActivity, R.string.error_search_failed);
            mActivity.setResult(Activity.RESULT_CANCELED);
            mActivity.finish();
        }
        return false;
    }

    /**
     * Cut us loose from the {@link SearchManager} by stopping listening to its messages
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch().removeListener(mSearchManagerId, this);
        }
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    public void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case SearchAdminActivity.REQUEST_CODE: { /* 1b923299-d966-4ed5-8230-c5a7c491053b */
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    mSearchSites = data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES, mSearchSites);
                }
                break;
            }
            case EditBookActivity.REQUEST_CODE: {/* 341ace23-c2c8-42d6-a71e-909a3a19ba99, 9e2c0b04-8217-4b49-9937-96d160104265 */
                if (resultCode == Activity.RESULT_OK) {
                    // Created a book; save the intent
                    mLastBookData = data;
                    // and set that as the default result
                    mActivity.setResult(resultCode, mLastBookData);

                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // if the edit was cancelled, set that as the default result code
                    mActivity.setResult(Activity.RESULT_CANCELED);
                }
                break;
            }
            default:
                // lowest level of our Fragment, see if we missed anything
                Logger.info(this, "BookSearchBaseFragment|onActivityResult|NOT HANDLED: requestCode=" + requestCode + ", resultCode=" + resultCode);
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        Tracker.enterOnSaveInstanceState(this, outState);

        // standard stuff we need
        outState.putLong(BKEY_SEARCH_MANAGER_ID, mSearchManagerId);
        outState.putParcelable(BKEY_LAST_BOOK_INTENT, mLastBookData);

        super.onSaveInstanceState(outState);
        Tracker.exitOnSaveInstanceState(this, outState);
    }
}
