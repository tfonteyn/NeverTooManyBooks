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

package com.hardbacknutter.nevertomanybooks.goodreads.api;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertomanybooks.utils.CredentialsException;

/**
 * book.show   —   Get the reviews for a book given a Goodreads book id.
 *
 * <a href="https://www.goodreads.com/api/index#book.show">
 * https://www.goodreads.com/api/index#book.show</a>
 *
 * @author Philip Warner
 */
public class ShowBookByIdApiHandler
        extends ShowBookApiHandler {

    /** Page url. */
    private static final String URL = GoodreadsManager.BASE_URL + "/book/show/%1$s.xml?key=%2$s";

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     *
     * @throws CredentialsException with GoodReads
     */
    public ShowBookByIdApiHandler(@NonNull final GoodreadsManager grManager)
            throws CredentialsException {
        super(grManager);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param id             the GoodReads book aka "work" id to get
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     *
     * @return the Bundle of book data.
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    public Bundle get(final long id,
                      final boolean fetchThumbnail)
            throws CredentialsException, BookNotFoundException, IOException {

        String url = String.format(URL, id, mManager.getDevKey());
        return getBookData(url, fetchThumbnail);
    }
}
