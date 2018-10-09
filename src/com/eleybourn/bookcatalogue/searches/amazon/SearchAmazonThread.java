package com.eleybourn.bookcatalogue.searches.amazon;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.tasks.TaskManager;

public class SearchAmazonThread extends SearchThread {

    public SearchAmazonThread(@NonNull final TaskManager manager,
                              @NonNull final String author,
                              @NonNull final String title,
                              @NonNull final String isbn,
                              final boolean fetchThumbnail) {
        super(manager, author, title, isbn, fetchThumbnail);
    }

    @Override
    protected void onRun() {
        this.doProgress(getString(R.string.searching_amazon_books), 0);

        try {
            AmazonManager.search(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
            if (mBookData.size() > 0) {
                // Look for series name and clear KEY_TITLE
                checkForSeriesName();
            }
        } catch (Exception e) {
            Logger.logError(e);
            showException(R.string.searching_amazon_books, e);
        }
    }

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchManager.SEARCH_AMAZON;
    }

}
