package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.ManagedSearchTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

/**
 *  GoogleBooks ManagedSearchTask as used by the {@link SearchSites.Site#getTask(TaskManager)}
 *
 */
public class SearchGoogleBooksTask extends ManagedSearchTask {

    public SearchGoogleBooksTask(final @NonNull String name,
                                 final @NonNull TaskManager manager) {
        super(name, manager);
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchSites.Site.SEARCH_GOOGLE;
    }

    @Override
    protected void runTask() {
        @StringRes
        final int R_ID_SEARCHING = R.string.searching_google_books;
        mTaskManager.sendTaskProgressMessage(this, R_ID_SEARCHING, 0);

        try {
            // manager checks the arguments
            GoogleBooksManager.search(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
            if (mBookData.size() > 0) {
                // Look for series name and clean KEY_TITLE
                checkForSeriesNameInTitle();
            }
        } catch (java.net.SocketTimeoutException e) {
            Logger.info(this,e.getLocalizedMessage());
            setFinalError(R_ID_SEARCHING, R.string.error_network_timeout);
        } catch (MalformedURLException | UnknownHostException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.error_search_configuration);
        } catch (IOException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.error_search_failed);
        } catch (Exception e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, e);
        }
    }

}
