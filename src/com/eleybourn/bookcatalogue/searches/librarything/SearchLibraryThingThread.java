package com.eleybourn.bookcatalogue.searches.librarything;

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;


/**
 * LibraryThing
 *
 * We always contact LibraryThing because it is a good source of Series data and thumbnails.
 * But it does require an ISBN AND a developer key.
*/
public class SearchLibraryThingThread extends SearchThread {

    public SearchLibraryThingThread(@NonNull final TaskManager manager,
                                    @NonNull final String author,
                                    @NonNull final String title,
                                    @NonNull final String isbn,
                                    final boolean fetchThumbnail) {
        super(manager, author, title, isbn, fetchThumbnail);
    }

    @Override
    protected void onRun() {
        if (IsbnUtils.isValid(mIsbn)) {
            @StringRes final int R_ID_SEARCHING = R.string.searching_library_thing;
            doProgress(getString(R_ID_SEARCHING), 0);
            LibraryThingManager ltm = new LibraryThingManager(BookCatalogueApp.getAppContext());
            // do we have a dev kev ?
            if (ltm.isAvailable()) {
                try {
                    ltm.search(mIsbn, mBookData, mFetchThumbnail);
                    // Look for series name and clean KEY_TITLE
                    checkForSeriesName();

                } catch (java.net.SocketTimeoutException e) {
                    showError(R_ID_SEARCHING, R.string.network_timeout);
                } catch (MalformedURLException | UnknownHostException e) {
                    Logger.error(e);
                    showError(R_ID_SEARCHING, R.string.search_configuration_error);

                } catch (IOException e) {
                    showError(R_ID_SEARCHING, R.string.error_search_failed);
                    Logger.error(e);

                } catch (Exception e) {
                    Logger.error(e);
                    showException(R_ID_SEARCHING, e);
                }
            }

        }
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchManager.SEARCH_LIBRARY_THING;
    }

}
