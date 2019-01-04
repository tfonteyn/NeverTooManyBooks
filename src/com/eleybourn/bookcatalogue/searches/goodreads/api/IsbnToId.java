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

import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * API call to get a Goodreads ID from an ISBN.
 *
 * NOTE: THIS API DOES NOT RETURN XML. The text output is the ID.
 *
 * @author Philip Warner
 */
public class IsbnToId extends ApiHandler {

    public IsbnToId(@NonNull final GoodreadsManager manager) {

        super(manager);
    }

    /*
     * Get the Goodreads book ID given an ISBN. Response contains the ID  as is.
     *	URL: http://www.goodreads.com/book/isbn_to_id    (sample url)
     *	HTTP method: GET
     *	Parameters:
     *	    isbn: The ISBN of the book to lookup.
     *	    key: Developer key (required).
     */
    public long isbnToId(@NonNull final String isbn)
            throws IOException,
                   NotAuthorizedException,
                   BookNotFoundException {

        HttpGet get = new HttpGet(GoodreadsManager.BASE_URL + "/book/isbn_to_id/" + isbn + "?key=" + mManager.getDevKey());
        String s = mManager.executeRaw(get);
        return Long.parseLong(s);
    }

}
