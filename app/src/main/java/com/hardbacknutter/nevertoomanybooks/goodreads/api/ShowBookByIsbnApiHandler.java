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

import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * book.show_by_isbn   —   Get the reviews for a book given an ISBN.
 *
 * <a href="https://www.goodreads.com/api/index#book.show_by_isbn">book.show_by_isbn</a>
 */
public class ShowBookByIsbnApiHandler
        extends ShowBookApiHandler {

    /** Page url. */
    private static final String URL = GoodreadsHandler.BASE_URL + "/book/isbn?"
                                      + "format=xml&isbn=%1$s&key=%2$s";

    /**
     * Constructor.
     *
     * @param context Current context
     * @param grAuth  Authentication handler
     *
     * @throws CredentialsException with GoodReads
     */
    public ShowBookByIsbnApiHandler(@NonNull final Context context,
                                    @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(context, grAuth);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param context        Current context
     * @param validIsbn      ISBN to use, must be valid
     * @param fetchThumbnail Set to {@code true} if we want to get thumbnails
     * @param bookData       Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return the Bundle of book data.
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    @NonNull
    public Bundle get(@NonNull final Context context,
                      @NonNull final String validIsbn,
                      @NonNull final boolean[] fetchThumbnail,
                      @NonNull final Bundle bookData)
            throws CredentialsException, Http404Exception, IOException {

        final String url = String.format(URL, validIsbn, mGoodreadsAuth.getDevKey());
        return getBookData(context, url, fetchThumbnail, bookData);
    }
}
