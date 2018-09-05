/*
 * @copyright 2011 evan
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.content.SearchRecentSuggestionsProvider;
import android.database.Cursor;
import android.net.Uri;

/**
 * @author evan
 *
 */
public class SearchSuggestionProvider extends SearchRecentSuggestionsProvider {
	private final static String AUTHORITY = "com.eleybourn.bookcatalogue.SearchSuggestionProvider";
	private final static int MODE = DATABASE_MODE_QUERIES;
	
	public SearchSuggestionProvider() {
		setupSuggestions(AUTHORITY, MODE);
	}
	
	private CatalogueDBAdapter mDb = null;
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		if (selectionArgs[0].isEmpty()) {
			return null;
		}
		if (mDb == null) {
			mDb = new CatalogueDBAdapter(getContext());
			mDb.open();
		}
		return mDb.fetchSearchSuggestions(selectionArgs[0]);
	}
}
