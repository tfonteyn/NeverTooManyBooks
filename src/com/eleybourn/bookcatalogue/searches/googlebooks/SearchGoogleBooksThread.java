package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.tasks.TaskManager;

public class SearchGoogleBooksThread extends SearchThread {

    public SearchGoogleBooksThread(@NonNull final TaskManager manager,
                                   @NonNull final String author,
                                   @NonNull final String title,
                                   @NonNull final String isbn,
                                   final boolean fetchThumbnail) {
        super(manager, author, title, isbn, fetchThumbnail);
    }

    @Override
    protected void onRun() {
        try {
            doProgress(getString(R.string.searching_google_books), 0);
            try {
                GoogleBooksManager.search(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
                if (mBookData.size() > 0) {
                    // Look for series name and clear KEY_TITLE
                    checkForSeriesName();
                }
            } catch (Exception e) {
                Logger.error(e);
                showException(R.string.searching_google_books, e);
            }


        } catch (Exception e) {
            Logger.error(e);
            showException(R.string.search_fail, e);
        }
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchManager.SEARCH_GOOGLE;
    }

}
