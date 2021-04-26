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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteParsingException;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * book.show_by_isbn   —   Get the reviews for a book given an ISBN.
 *
 * <a href="https://www.goodreads.com/api/index#book.show_by_isbn">book.show_by_isbn</a>
 */
public class ShowBookByIsbnApiHandler
        extends ShowBookApiHandler {

    /** Page url. */
    private static final String BY_ISBN = GoodreadsManager.BASE_URL + "/book/isbn?"
                                          + "format=xml&isbn=%1$s&key=%2$s";

    /**
     * Constructor.
     *
     * @param context Current context
     * @param grAuth  Authentication handler
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    public ShowBookByIsbnApiHandler(@NonNull final Context context,
                                    @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(context, grAuth);
    }

    /**
     * Perform a search and handle the results.
     *
     * @param validIsbn   ISBN to use, must be valid
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return the Bundle of book data.
     *
     * @throws SiteParsingException on a decoding/parsing of data issue
     * @throws IOException          on other failures
     */
    @NonNull
    public Bundle searchByIsbn(@NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers,
                               @NonNull final Bundle bookData)
            throws SiteParsingException, IOException, DiskFullException {

        final String url = String.format(BY_ISBN, validIsbn, mGrAuth.getDevKey());
        return searchBook(url, fetchCovers, bookData);
    }

    /**
     * Perform a search and extract/fetch only the cover.
     *
     * @param validIsbn ISBN to use, must be valid
     * @param bookData  Bundle to update <em>(passed in to allow mocking)</em>
     *
     * @return fileSpec, or {@code null} if no image found.
     *
     * @throws SiteParsingException on a decoding/parsing of data issue
     * @throws IOException             on other failures
     */
    @Nullable
    public String searchCoverByIsbn(@NonNull final String validIsbn,
                                    @NonNull final Bundle bookData)
            throws SiteParsingException, IOException, DiskFullException {

        final String url = String.format(BY_ISBN, validIsbn, mGrAuth.getDevKey());
        return searchCoverImage(url, bookData);
    }
}
