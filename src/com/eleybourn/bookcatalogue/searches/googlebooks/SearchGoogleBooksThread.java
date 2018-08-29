package com.eleybourn.bookcatalogue.searches.googlebooks;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.utils.TaskManager;
import com.eleybourn.bookcatalogue.debug.Logger;

public class SearchGoogleBooksThread extends SearchThread {

	public SearchGoogleBooksThread(TaskManager manager,
								   String author, String title, String isbn, boolean fetchThumbnail) {
		super(manager, author, title, isbn, fetchThumbnail);
	}

	@Override
	protected void onRun() {
		try {
			doProgress(getString(R.string.searching_google_books), 0);
			try {
				GoogleBooksManager.searchGoogle(mIsbn, mAuthor, mTitle, mBookData, mFetchThumbnail);
			} catch (Exception e) {
				Logger.logError(e);
				showException(R.string.searching_google_books, e);
			}

			// Look for series name and clear KEY_TITLE
			checkForSeriesName();

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
		return SearchManager.SEARCH_GOOGLE;
	}

}
