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

/**
 * Class to call the search.books api (using a Goodreads 'work' ID).
 *
 * @author Philip Warner
 */
public class ShowBookByIdApiHandler
        extends ShowBookApiHandler {

    public ShowBookByIdApiHandler(@NonNull final GoodreadsManager manager) {
        super(manager, true);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param fetchThumbnail Set to <tt>true</tt> if we want to get a thumbnail
     *
     * @return the array of GoodreadsWork objects.
     */
    @NonNull
    public Bundle get(final long workId,
                      final boolean fetchThumbnail)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        // Setup API call
        String urlBase = GoodreadsManager.BASE_URL + "/book/show/%1$s.xml?key=%2$s";
        String url = String.format(urlBase, workId, mManager.getDevKey());
        HttpGet get = new HttpGet(url);

        return sendRequest(get, fetchThumbnail);
    }
}
