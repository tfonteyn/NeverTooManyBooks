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

import android.os.Bundle;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 * Class to call the search.books api (using a Goodreads 'work' ID).
 *
 * @author Philip Warner
 */
public class ShowBookByIdApiHandler extends ShowBookApiHandler {

    public ShowBookByIdApiHandler(final @NonNull GoodreadsManager manager) {
        super(manager, true);
    }

    /**
     * Perform a search and handle the results.
     *
     * @return the array of GoodreadsWork objects.
     */
    @NonNull
    public Bundle get(final long workId, final boolean fetchThumbnail) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException,
            BookNotFoundException, IOException, NetworkException {

        // Setup API call
        final String urlBase = GoodreadsManager.GOODREADS_API_ROOT + "/book/show/%1$s.xml?key=%2$s";
        final String url = String.format(urlBase, workId, mManager.getDevKey());
        HttpGet get = new HttpGet(url);

        return sendRequest(get, fetchThumbnail);
    }

}
