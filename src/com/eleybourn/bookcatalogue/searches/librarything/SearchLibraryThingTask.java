package com.eleybourn.bookcatalogue.searches.librarything;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.ManagedSearchTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;


/**
 * LibraryThing ManagedSearchTask as used by the {@link SearchSites.Site#getTask(TaskManager)}
 *
 * We always contact LibraryThing because it is a good source of Series data and thumbnails.
 * But it does require an ISBN AND a developer key.
 */
public class SearchLibraryThingTask extends ManagedSearchTask {

    public SearchLibraryThingTask(@NonNull final String name,
                                  @NonNull final TaskManager manager) {
        super(name, manager);
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchSites.Site.SEARCH_LIBRARY_THING;
    }

    @Override
    protected void runTask() {
        @StringRes final int R_ID_SEARCHING = R.string.searching_library_thing;
        mTaskManager.sendTaskProgressMessage(this, R_ID_SEARCHING, 0);

        LibraryThingManager ltm = new LibraryThingManager();
        if (!ltm.isAvailable()) {
            return;
        }

        try {
            // manager checks the arguments
            ltm.search(mIsbn, mBookData, mFetchThumbnail);
            if (mBookData.size() > 0) {
                // Look for series name in the book title and clean KEY_TITLE
                checkForSeriesNameInTitle();
            }
        } catch (java.net.SocketTimeoutException e) {
            Logger.info(this,e.getLocalizedMessage());
            setFinalError(R_ID_SEARCHING, R.string.error_network_timeout);
        } catch (MalformedURLException | UnknownHostException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, R.string.error_search_configuration);
        } catch (IOException e) {
            setFinalError(R_ID_SEARCHING, R.string.error_search_failed);
            Logger.error(e);
        } catch (RuntimeException e) {
            Logger.error(e);
            setFinalError(R_ID_SEARCHING, e);
        }
    }
}
