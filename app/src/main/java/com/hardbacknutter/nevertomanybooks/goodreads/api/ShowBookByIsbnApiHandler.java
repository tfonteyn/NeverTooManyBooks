/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.goodreads.api;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertomanybooks.utils.ISBN;

/**
 * book.show_by_isbn   —   Get the reviews for a book given an ISBN.
 *
 * <a href="https://www.goodreads.com/api/index#book.show_by_isbn">
 * https://www.goodreads.com/api/index#book.show_by_isbn</a>
 * <p>
 * This also accepts an ASIN as the isbn.
 */
public class ShowBookByIsbnApiHandler
        extends ShowBookApiHandler {

    /** Page url. */
    private static final String URL = GoodreadsManager.BASE_URL
                                      + "/book/isbn?format=xml&isbn=%1$s&key=%2$s";

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     *
     * @throws CredentialsException with GoodReads
     */
    public ShowBookByIsbnApiHandler(@NonNull final Context context,
                                    @NonNull final GoodreadsManager grManager)
            throws CredentialsException {
        super(context, grManager);
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
            throws CredentialsException, BookNotFoundException, IOException {

        if (!ISBN.isValid(isbn)) {
            throw new BookNotFoundException(isbn);
        }

        String url = String.format(URL, isbn, mManager.getDevKey());
        return getBookData(url, fetchThumbnail);
    }
}
