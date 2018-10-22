package com.eleybourn.bookcatalogue.searches.googlebooks;

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.tasks.TaskManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

public class SearchGoogleBooksThread extends SearchThread {

    public SearchGoogleBooksThread(@NonNull final TaskManager manager,
                                   @NonNull final String author,
                                   @NonNull final String title,
                                   @NonNull final String isbn,
                                   final boolean fetchThumbnail) {
        super(manager, author, title, isbn, fetchThumbnail);
        setName("SearchGoogleBooksThread isbn=" + isbn);

    }

    @Override
    protected void runTask() {
        @StringRes
        final int R_ID_SEARCHING = R.string.searching_google_books;
        doProgress(getString(R_ID_SEARCHING), 0);
        try {
            GoogleBooksManager.search(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
            if (mBookData.size() > 0) {
                // Look for series name and clean KEY_TITLE
                checkForSeriesName();
            }
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

    /**
     * Return the global ID for this searcher
     */
    @Override
    public int getSearchId() {
        return SearchManager.SEARCH_GOOGLE;
    }

}
