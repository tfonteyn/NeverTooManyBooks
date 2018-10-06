package com.eleybourn.bookcatalogue.searches.isfdb;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.tasks.TaskManager;

public class SearchISFDBThread extends SearchThread {

    public SearchISFDBThread(@NonNull final TaskManager manager,
                             @NonNull final String author,
                             @NonNull final String title,
                             @NonNull final String isbn,
                             final boolean fetchThumbnail) {
        super(manager, author, title, isbn, fetchThumbnail);
    }

    @Override
    protected void onRun() {
        try {
            doProgress(getString(R.string.searching_isfdb), 0);
            try {
                ISFDBManager.search(mIsbn, mAuthor, mTitle, mBook, mFetchThumbnail);
                if (mBook.size() > 0) {
                    // Look for series name and clear KEY_TITLE
                    checkForSeriesName();
                }
            } catch (Exception e) {
                Logger.logError(e);
                showException(R.string.searching_isfdb, e);
            }


        } catch (Exception e) {
            Logger.logError(e);
            showException(R.string.search_fail, e);
        }
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchManager.SEARCH_ISFDB;
    }

}
