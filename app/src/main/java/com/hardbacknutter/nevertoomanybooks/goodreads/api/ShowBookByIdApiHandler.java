/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * book.show   —   Get the reviews for a book given a Goodreads book id.
 *
 * <a href="https://www.goodreads.com/api/index#book.show">book.show</a>
 */
public class ShowBookByIdApiHandler
        extends ShowBookApiHandler {

    /** Page url. */
    private static final String BY_ID = GoodreadsManager.BASE_URL + "/book/show/%1$s.xml?key=%2$s";

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException with GoodReads
     */
    public ShowBookByIdApiHandler(@NonNull final Context appContext,
                                  @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param grBookId       the GoodReads book aka "work" id to get
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
    public Bundle searchByExternalId(final long grBookId,
                                     @NonNull final boolean[] fetchThumbnail,
                                     @NonNull final Bundle bookData)
            throws CredentialsException, Http404Exception, IOException {

        final String url = String.format(BY_ID, grBookId, mGrAuth.getDevKey());
        return searchBook(url, fetchThumbnail, bookData);
    }
}
