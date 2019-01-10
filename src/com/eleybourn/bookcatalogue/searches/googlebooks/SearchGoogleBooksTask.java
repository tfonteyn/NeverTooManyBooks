package com.eleybourn.bookcatalogue.searches.googlebooks;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.ManagedSearchTask;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

/**
 * GoogleBooks ManagedSearchTask as used by the {@link SearchSites.Site#getTask(TaskManager)}.
 */
public class SearchGoogleBooksTask
        extends ManagedSearchTask {

    /** progress title. */
    @StringRes
    private static final int R_ID_SEARCHING = R.string.searching_google_books;

    public SearchGoogleBooksTask(@NonNull final String name,
                                 @NonNull final TaskManager manager) {
        super(name, manager);
    }

    /**
     * @return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchSites.Site.SEARCH_GOOGLE;
    }

    @Override
    protected void runTask() {

        mTaskManager.sendTaskProgressMessage(this, R_ID_SEARCHING, 0);

        try {
            // manager checks the arguments
            GoogleBooksManager.search(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
            if (mBookData.size() > 0) {
                // Look for series name and clean KEY_TITLE
                checkForSeriesNameInTitle();
            }
        } catch (java.net.SocketTimeoutException e) {
            Logger.info(this, e.getLocalizedMessage());
            setFinalError(R_ID_SEARCHING, R.string.error_network_timeout);
        } catch (MalformedURLException | UnknownHostException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.error_search_configuration);
        } catch (IOException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.error_search_failed);
        } catch (RuntimeException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, e);
        }
    }

}
