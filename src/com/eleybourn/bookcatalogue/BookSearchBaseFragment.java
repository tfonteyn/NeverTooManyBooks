package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;

import java.util.Objects;

public abstract class BookSearchBaseFragment
        extends Fragment
        implements SearchManager.SearchManagerListener {

    static final int REQ_BOOK_EDIT = 0;
    /**
     * Optionally limit the sites to search on.
     * By default uses {@link SearchSites.Site#SEARCH_ALL}
     */
    private static final String REQUEST_BKEY_SEARCH_SITES = "SearchSites";
    /** stores an active search id, or 0 when none active. */
    private static final String BKEY_SEARCH_MANAGER_ID = "SearchManagerId";
    /** the last book data (intent) we got from a successful EditBook. */
    private static final String BKEY_LAST_BOOK_INTENT = "LastBookIntent";
    /** activity request code */
    private static final int REQ_PREFERRED_SEARCH_SITES = 10;

    protected BookSearchActivity mActivity;

    /** Database instance. */
    protected DBA mDb;
    /** Objects managing current search. */
    long mSearchManagerId;
    /** The last Intent returned as a result of creating a book. */
    @Nullable
    Intent mLastBookData;
    /** sites to search on. Can be overridden by the user (option menu). */
    private int mSearchSites = SearchSites.Site.SEARCH_ALL;

    /**
     * Called to do initial creation of a fragment.  This is called after
     * {@link #onAttach(Activity)} and before
     * {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * <p>
     * Note that this can be called while the fragment's activity is
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
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // make sure {@link #onCreateOptionsMenu} is called
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        // cache to avoid multiple calls to requireActivity()
        mActivity = (BookSearchActivity) requireActivity();

        super.onActivityCreated(savedInstanceState);

        mDb = new DBA(mActivity);

        if (savedInstanceState != null) {
            mSearchManagerId = savedInstanceState.getLong(BKEY_SEARCH_MANAGER_ID);
            // optional, use ALL if not there
            mSearchSites = savedInstanceState.getInt(REQUEST_BKEY_SEARCH_SITES,
                                                     SearchSites.Site.SEARCH_ALL);
        } else {
            Bundle args = getArguments();
            //noinspection ConstantConditions
            mSearchManagerId = args.getLong(BKEY_SEARCH_MANAGER_ID);
            // optional, use ALL if not there
            mSearchSites = args.getInt(REQUEST_BKEY_SEARCH_SITES, SearchSites.Site.SEARCH_ALL);
        }

        if ((mSearchSites & SearchSites.Site.SEARCH_LIBRARY_THING) != 0) {
            LibraryThingManager.showLtAlertIfNecessary(mActivity, false, "search");
        }

        if (!NetworkUtils.isNetworkAvailable(mActivity)) {
            StandardDialogs.showUserMessage(mActivity, R.string.error_no_internet_connection);
        }
    }

    /**
     * @param menu The options menu in which you place your items.
     *
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES, 0, R.string.tab_lbl_search_sites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(requireContext(), SearchAdminActivity.class);
                intent.putExtra(SearchAdminActivity.REQUEST_BKEY_TAB,
                                SearchAdminActivity.TAB_SEARCH_ORDER);
                startActivityForResult(intent, REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * (re)connect with the {@link SearchManager} by starting to listen to its messages.
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch()
                         .addListener(mSearchManagerId, this, true);
        }
    }

    /**
     * Start the actual search with the {@link SearchManager} in the background.
     * <p>
     * The results will arrive in
     * {@link SearchManager.SearchManagerListener#onSearchFinished(boolean, Bundle)}
     *
     * @return <tt>true</tt> if search was started.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean startSearch(@NonNull final String authorSearchText,
                        @NonNull final String titleSearchText,
                        @NonNull final String isbnSearchText) {

        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, "startSearch"
                    + "|isbn=" + isbnSearchText
                    + "|author=" + authorSearchText
                    + "|title=" + titleSearchText);
        }

        try {
            // Start the lookup in a background search task.
            final SearchManager searchManager =
                    new SearchManager(mActivity.getTaskManager(), this);
            mSearchManagerId = searchManager.getId();

            Tracker.handleEvent(this, Tracker.States.Running,
                                "Created SearchManager=" + mSearchManagerId);

            mActivity.getTaskManager().sendHeaderTaskProgressMessage(
                    getString(R.string.progress_msg_searching));
            // kick of the searches
            searchManager.search(mSearchSites, authorSearchText, titleSearchText,
                                 isbnSearchText, true);
            return true;

        } catch (RuntimeException e) {
            Logger.error(e);
            StandardDialogs.showUserMessage(mActivity, R.string.error_search_failed);
            mActivity.setResult(Activity.RESULT_CANCELED);
            mActivity.finish();
        }
        return false;
    }

    /**
     * Cut us loose from the {@link SearchManager} by stopping listening to its messages.
     */
    @Override
    @CallSuper
    public void onPause() {
        if (mSearchManagerId != 0) {
            SearchManager.getMessageSwitch().removeListener(mSearchManagerId, this);
        }
        super.onPause();
    }

    @Override
    @CallSuper
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    mSearchSites = data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES,
                                                    mSearchSites);
                }
                break;

            case REQ_BOOK_EDIT:
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

            default:
                // lowest level of our Fragment, see if we missed anything
                Logger.info(this,
                            "BookSearchBaseFragment|onActivityResult|NOT HANDLED:"
                                    + " requestCode=" + requestCode + ", resultCode=" + resultCode);
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putLong(BKEY_SEARCH_MANAGER_ID, mSearchManagerId);
        outState.putParcelable(BKEY_LAST_BOOK_INTENT, mLastBookData);

        super.onSaveInstanceState(outState);
    }
}
