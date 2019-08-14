/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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

/**
 * book.show   —   Get the reviews for a book given a Goodreads book id.
 *
 * <a href="https://www.goodreads.com/api/index#book.show">
 * https://www.goodreads.com/api/index#book.show</a>
 */
public class ShowBookByIdApiHandler
        extends ShowBookApiHandler {

    /** Page url. */
    private static final String URL = GoodreadsManager.BASE_URL + "/book/show/%1$s.xml?key=%2$s";

    /**
     * Constructor.
     *
     * @param context   Current context
     * @param grManager the Goodreads Manager
     *
     * @throws CredentialsException with GoodReads
     */
    public ShowBookByIdApiHandler(@NonNull final Context context,
                                  @NonNull final GoodreadsManager grManager)
            throws CredentialsException {
        super(context, grManager);
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
