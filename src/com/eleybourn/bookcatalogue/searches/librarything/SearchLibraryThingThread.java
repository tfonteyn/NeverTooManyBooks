package com.eleybourn.bookcatalogue.searches.librarything;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;


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
            this.doProgress(getString(R.string.searching_library_thing), 0);
            LibraryThingManager ltm = new LibraryThingManager(BookCatalogueApp.getAppContext());
            if (ltm.isAvailable()) {
                try {
                    ltm.search(mIsbn, mBookInfo, mFetchThumbnail);
                    // Look for series name and clear KEY_TITLE
                    checkForSeriesName();
                } catch (Exception e) {
                    Logger.logError(e);
                    showException(R.string.searching_library_thing, e);
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
