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

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;

import com.eleybourn.bookcatalogue.goodreads.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.ISBN;

/**
 * Class to call the search.books api (using an ISBN).
 *
 * @author Philip Warner
 */
public class ShowBookByIsbnApiHandler
        extends ShowBookApiHandler {


    /**
     * Constructor.
     *
     * @param manager GoodreadsManager
     */
    public ShowBookByIsbnApiHandler(@NonNull final GoodreadsManager manager) {
        // TODO: If goodreads fix signed book.show_by_isbn requests, change false to true...
        super(manager, true);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param isbn           to use
     * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
     *
     * @return the Bundle of book data.
     */
    @NonNull
    public Bundle get(@NonNull final String isbn,
                      final boolean fetchThumbnail)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        if (!ISBN.isValid(isbn)) {
            throw new ISBN.IsbnInvalidException(isbn);
        }

        // Setup API call
        String urlBase = GoodreadsManager.BASE_URL + "/book/isbn?format=xml&isbn=%1$s&key=%2$s";
        String url = String.format(urlBase, isbn, mManager.getDevKey());
        HttpGet get = new HttpGet(url);

        return sendRequest(get, fetchThumbnail);
    }

}
