package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import java.util.Objects;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.searches.SearchCoordinator;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.settings.SearchAdminActivity;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.utils.Utils;
import com.eleybourn.bookcatalogue.viewmodels.BookSearchBaseModel;

/**
 * Optionally limit the sites to search on by setting {@link UniqueId#BKEY_SEARCH_SITES}.
 * By default uses {@link SearchSites#SEARCH_ALL}.
 */
public abstract class BookSearchBaseFragment
        extends Fragment {

    /** Fragment manager tag. */
    private static final String TAG = "BookSearchBaseFragment";

    /** stores an active search id, or 0 when none active. */
    public static final String BKEY_SEARCH_COORDINATOR_ID = TAG + ":SearchCoordinatorId";
    /** the last book data (intent) we got from a successful EditBook. */
    private static final String BKEY_LAST_BOOK_INTENT = TAG + ":LastBookIntent";

    /** hosting activity. */
    AppCompatActivity mActivity;
    TaskManager mTaskManager;

    /** the ViewModel. */
    BookSearchBaseModel mBookSearchBaseModel;

    abstract SearchCoordinator.SearchFinishedListener getSearchFinishedListener();

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
        mTaskManager = ((BookSearchActivity) mActivity).getTaskManager();
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // make sure {@link #onCreateOptionsMenu} is called
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mBookSearchBaseModel = ViewModelProviders.of(this).get(BookSearchBaseModel.class);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mBookSearchBaseModel.init(args);

        if ((mBookSearchBaseModel.getSearchSites() & SearchSites.LIBRARY_THING) != 0) {
            //noinspection ConstantConditions
            LibraryThingManager.showLtAlertIfNecessary(getContext(), false, "search");
        }

        // Check general network connectivity. If none, WARN the user.
        if (!NetworkUtils.isNetworkAvailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_HIDE_KEYBOARD,
                 MenuHandler.ORDER_HIDE_KEYBOARD, R.string.menu_hide_keyboard)
            .setIcon(R.drawable.ic_keyboard_hide)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES,
                 MenuHandler.ORDER_SEARCH_SITES, R.string.lbl_search_sites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_HIDE_KEYBOARD:
                //noinspection ConstantConditions
                Utils.hideKeyboard(getView());
                return true;

            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(getContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminActivity.REQUEST_BKEY_TAB,
                                  SearchAdminActivity.TAB_ORDER);
                startActivityForResult(intent, UniqueId.REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * (re)connect with the {@link SearchCoordinator} by starting to listen to its messages.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mBookSearchBaseModel.getSearchCoordinatorId() != 0) {
            SearchCoordinator.MESSAGE_SWITCH
                    .addListener(mBookSearchBaseModel.getSearchCoordinatorId(), true,
                                 getSearchFinishedListener());
        }
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * The results will arrive in
     * {@link SearchCoordinator.SearchFinishedListener#onSearchFinished(boolean, Bundle)}
     *
     * @return {@code true} if search was started.
     */
    boolean startSearch() {

        // check if we have an active search, if so, quit silently.
        if (mBookSearchBaseModel.getSearchCoordinatorId() != 0) {
            return false;
        }

        //sanity check
        if (!mBookSearchBaseModel.hasSearchData()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.warning_required_at_least_one);
            return false;
        }
        // Don't start search if we have no approved network... FAIL.
        if (!NetworkUtils.isNetworkAvailable()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_no_internet_connection);
            return false;
        }

        try {
            // Start the lookup in a background search task.
            final SearchCoordinator searchCoordinator =
                    new SearchCoordinator(mTaskManager, getSearchFinishedListener());
            mBookSearchBaseModel.setSearchCoordinator(searchCoordinator.getId());

            mTaskManager.sendHeaderUpdate(R.string.progress_msg_searching);
            // kick of the searches
            searchCoordinator.search(mBookSearchBaseModel.getSearchSites(),
                                     mBookSearchBaseModel.getIsbnSearchText(),
                                     mBookSearchBaseModel.getAuthorSearchText(),
                                     mBookSearchBaseModel.getTitleSearchText(),
                                     mBookSearchBaseModel.getPublisherSearchText(),
                                     true);

            // reset the details so we don't restart the search unnecessarily
            mBookSearchBaseModel.clearSearchText();

            return true;

        } catch (@NonNull final RuntimeException e) {
            Logger.error(this, e);
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_search_failed);

        }
        mActivity.setResult(Activity.RESULT_CANCELED);
        mActivity.finish();
        return false;
    }

    /**
     * Cut us loose from the {@link SearchCoordinator} by stopping listening to its messages.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        if (mBookSearchBaseModel.getSearchCoordinatorId() != 0) {
            SearchCoordinator.MESSAGE_SWITCH.removeListener(
                    mBookSearchBaseModel.getSearchCoordinatorId(),
                    getSearchFinishedListener());
        }
        super.onPause();
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case UniqueId.REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    mBookSearchBaseModel.setSearchSites(
                            data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES,
                                             mBookSearchBaseModel.getSearchSites()));
                }
                break;

            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    // Created a book; save the intent
                    mBookSearchBaseModel.setLastBookData(data);
                    // and set it as the default result
                    mActivity.setResult(resultCode, mBookSearchBaseModel.getLastBookData());

                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // if the edit was cancelled, set that as the default result code
                    mActivity.setResult(Activity.RESULT_CANCELED);
                }
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.warnWithStackTrace(this, "BookSearchBaseFragment.onActivityResult",
                                              "NOT HANDLED:",
                                              "requestCode=" + requestCode,
                                              "resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BKEY_SEARCH_COORDINATOR_ID, mBookSearchBaseModel.getSearchCoordinatorId());
        outState.putParcelable(BKEY_LAST_BOOK_INTENT, mBookSearchBaseModel.getLastBookData());
    }
}
