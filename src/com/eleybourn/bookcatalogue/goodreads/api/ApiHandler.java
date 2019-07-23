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

package com.eleybourn.bookcatalogue.goodreads.api;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;

/**
 * Base class for all Goodreads API handler classes.
 * <p>
 * The job of an API handler is to implement a method to run the API (e.g. 'search' in
 * {@link SearchBooksApiHandler} and to process the output.
 *
 * @author Philip Warner
 */
abstract class ApiHandler
        extends ApiHandlerNative {

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     */
    ApiHandler(@NonNull final GoodreadsManager grManager) {
        super(grManager);
    }
}
