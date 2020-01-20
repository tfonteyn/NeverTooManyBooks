/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.BookNotFoundException;
import com.hardbacknutter.nevertoomanybooks.utils.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

/**
 * book.show_by_isbn   —   Get the reviews for a book given an ISBN.
 *
 * <a href="https://www.goodreads.com/api/index#book.show_by_isbn">book.show_by_isbn</a>
 */
public class ShowBookByIsbnApiHandler
        extends ShowBookApiHandler {

    /** Page url. */
    private static final String URL = GoodreadsManager.BASE_URL + "/book/isbn?"
                                      + "format=xml&isbn=%1$s&key=%2$s";

    /**
     * Constructor.
     *
     * @param context   Current context
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
     * @param isbn           ISBN to use, must be valid
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to save results in (passed in to allow mocking)
     *
     * @return the Bundle of book data.
     *
     * @throws CredentialsException  with GoodReads
     * @throws BookNotFoundException GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException           on other failures
     */
    @NonNull
    public Bundle get(@NonNull final ISBN isbn,
                      @NonNull final boolean[] fetchThumbnail,
                      @NonNull final Bundle bookData)
            throws CredentialsException, BookNotFoundException, IOException {

        String url = String.format(URL, isbn.asText(), mManager.getDevKey());
        return getBookData(url, fetchThumbnail, bookData);
    }
}
