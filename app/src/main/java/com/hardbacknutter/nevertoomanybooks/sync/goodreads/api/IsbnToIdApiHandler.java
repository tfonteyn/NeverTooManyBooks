/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.api;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * book.isbn_to_id   —   Get Goodreads book IDs given ISBNs.
 *
 * <a href="https://www.goodreads.com/api/index#book.isbn_to_id">book.isbn_to_id</a>
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
     * @param context Current context
     * @param grAuth  Authentication handler
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    public IsbnToIdApiHandler(@NonNull final Context context,
                              @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(context, grAuth);
        mGrAuth.hasValidCredentialsOrThrow(context);
    }

    /**
     * Get the Goodreads book id given an ISBN.
     *
     * @param isbn to search for
     *
     * @return Goodreads book ID
     *
     * @throws CredentialsException if there are no valid credentials available
     * @throws IOException          on failures
     */
    public long isbnToId(@NonNull final String isbn)
            throws CredentialsException, IOException {

        final String url = String.format(URL, isbn, mGrAuth.getDevKey());
        final String id = executeRawGet(url, true);
        return Long.parseLong(id);
    }
}
