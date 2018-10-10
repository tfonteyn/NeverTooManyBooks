/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.searches.goodreads.api;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;

/**
 * Base class for all goodreads API handler classes.
 * 
 * The job of an API handler is to implement a method to run the API (eg. 'search' in 
 * SearchBooksApiHandler) and to process the output.
 * 
 * @author Philip Warner
 *
 */
abstract class ApiHandler {
	@NonNull
    final GoodreadsManager mManager;

	/** XmlFilter root object. Used in extracting data file XML results. */
	final XmlFilter mRootFilter = new XmlFilter("");

	public ApiHandler(@NonNull final GoodreadsManager manager) {
		mManager = manager;
	}
}
