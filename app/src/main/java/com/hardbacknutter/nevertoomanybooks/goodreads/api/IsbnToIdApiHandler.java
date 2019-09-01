/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;

/**
 * book.isbn_to_id   —   Get Goodreads book IDs given ISBNs.
 *
 * <a href="https://www.goodreads.com/api/index#book.isbn_to_id">
 * https://www.goodreads.com/api/index#book.isbn_to_id</a>
 *
 * <strong>Note:</strong> THIS DOES NOT RETURN XML. The text output is the ID.
 */
public class IsbnToIdApiHandler
        extends ApiHandler {

    /** Param 1: isbn; param 2: dev key. */
    private static final String URL = GoodreadsManager.BASE_URL + "/book/isbn_to_id/%1$s?key=%2$s";

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     *
     * @throws CredentialsException with GoodReads
     */
    public IsbnToIdApiHandler(@NonNull final GoodreadsManager grManager)
            throws CredentialsException {
        super(grManager);
        if (!grManager.hasValidCredentials()) {
            throw new CredentialsException(R.string.goodreads);
        }
    }

    /**
     * Get the Goodreads book id given an ISBN.
     *
     * @param isbn with some luck, the ISBN for the requested book
     *
     * @return Goodreads book ID
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    public long isbnToId(@NonNull final String isbn)
            throws CredentialsException, BookNotFoundException, IOException {

        String url = String.format(URL, isbn, mManager.getDevKey());
        String id = executeRawGet(url, true);
        return Long.parseLong(id);
    }
}
