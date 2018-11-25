package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.ManagedSearchTask;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

/**
 *  ISFDB ManagedSearchTask as used by the {@link SearchSites.Site#getTask(TaskManager)}
 *
 */
public class SearchISFDBTask extends ManagedSearchTask {

    public SearchISFDBTask(final @NonNull String name,
                           final @NonNull TaskManager manager) {
        super(name, manager);
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchSites.Site.SEARCH_ISFDB;
    }

    @Override
    protected void runTask() {
        final @StringRes int R_ID_SEARCHING = R.string.searching_isfdb;
        doProgress(getString(R_ID_SEARCHING), 0);

        try {
            // manager checks the arguments
            ISFDBManager.search(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
            if (mBookData.size() > 0) {
                // Look for series name and clean KEY_TITLE
                checkForSeriesNameInTitle();
            }
        } catch (java.net.SocketTimeoutException e) {
            showError(R_ID_SEARCHING, R.string.error_network_timeout);

        } catch (MalformedURLException | UnknownHostException e) {
            Logger.error(e);
            showError(R_ID_SEARCHING, R.string.error_search_configuration);

        } catch (IOException e) {
            showError(R_ID_SEARCHING, R.string.error_search_failed);
            Logger.error(e);

        } catch (Exception e) {
            Logger.error(e);
            showException(R_ID_SEARCHING, e);
        }
    }



}
