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

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;
import com.eleybourn.bookcatalogue.utils.CredentialsException;
import com.eleybourn.bookcatalogue.utils.ISBN;

/**
 * book.show_by_isbn   —   Get the reviews for a book given an ISBN.
 *
 * <a href="https://www.goodreads.com/api/index#book.show_by_isbn">
 * https://www.goodreads.com/api/index#book.show_by_isbn</a>
 * <p>
 * This also accepts an ASIN as the isbn.
 *
 * @author Philip Warner
 */
public class ShowBookByIsbnApiHandler
        extends ShowBookApiHandler {

    private static final String URL = GoodreadsManager.BASE_URL
            + "/book/isbn?format=xml&isbn=%1$s&key=%2$s";

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     *
     * @throws CredentialsException with GoodReads
     */
    public ShowBookByIsbnApiHandler(@NonNull final GoodreadsManager grManager)
            throws CredentialsException {
        super(grManager);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param isbn           ISBN or ASIN to search for
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     *
     * @return the Bundle of book data.
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    public Bundle get(@NonNull final String isbn,
                      final boolean fetchThumbnail)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        if (!ISBN.isValid(isbn)) {
            throw new BookNotFoundException(isbn);
        }

        String url = String.format(URL, isbn, mManager.getDevKey());
        return getBookData(url, fetchThumbnail);
    }
}
