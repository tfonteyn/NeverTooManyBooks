package com.eleybourn.bookcatalogue.searches.librarything;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.SearchThread;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.debug.Logger;


public class SearchLibraryThingThread extends SearchThread {

	public SearchLibraryThingThread(TaskManager manager,
			String author, String title, String isbn, boolean fetchThumbnail) {
		super(manager, author, title, isbn, fetchThumbnail);
	}

	@Override
	protected void onRun() {
		//
		//	LibraryThing
		//
		//	We always contact LibraryThing because it is a good source of Series data and thumbnails. But it 
		//	does require an ISBN AND a developer key.
		//
		if (mIsbn != null && !mIsbn.trim().isEmpty()) {
			String isbn = mIsbn;
			if (!isbn.isEmpty()) {
				this.doProgress(getString(R.string.searching_library_thing), 0);
				LibraryThingManager ltm = new LibraryThingManager(BookCatalogueApp.getAppContext());
				if (ltm.isAvailable()) {
					try {
						ltm.searchByIsbn(isbn, mFetchThumbnail, mBookData);
						// Look for series name and clear KEY_TITLE
						checkForSeriesName();
					} catch (Exception e) {
						Logger.logError(e);
						showException(R.string.searching_library_thing, e);
					}
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
