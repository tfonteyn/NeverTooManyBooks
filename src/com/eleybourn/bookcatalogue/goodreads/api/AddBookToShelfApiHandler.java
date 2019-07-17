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


import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

/**
 * Class to add a book to a shelf. In this case, we do not care about the data returned.
 * <p>
 * ENHANCE: Parse the result and store it against the bookshelf in the database.
 * Currently, this is not a simple thing to do because bookshelf naming rules in
 * goodreads are much more restrictive: no spaces, punctuation (at least).
 * <p>
 * Need to add the following to bookshelf table:
 * - gr_bookshelf_id
 * - (perhaps) gr_bookshelf_name
 * <p>
 * <p>
 * Typical response.
 * <pre>
 * {@code
 *  <shelf>
 *      <created-at type='datetime'>2012-01-02T19:07:12-08:00</created-at>
 *      <id type='integer'>167676018</id>
 *      <position type='integer'>13</position>
 *      <review-id type='integer'>255221284</review-id>
 *      <updated-at type='datetime'>2012-01-02T19:07:12-08:00</updated-at>
 *      <user-shelf-id type='integer'>16737904</user-shelf-id>
 *      <name>sci-fi-fantasy</name>
 *  </shelf>
 * }
 * </pre>
 *
 * @author Philip Warner
 */
public class AddBookToShelfApiHandler
        extends ApiHandler {

    private long mReviewId;
    private final XmlHandler mHandleReviewId = context -> {
        try {
            mReviewId = Long.parseLong(context.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
        }
    };

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     */
    public AddBookToShelfApiHandler(@NonNull final GoodreadsManager grManager) {
        super(grManager);
        buildFilters();
    }

    /**
     * Add the passed book to the passed shelf.
     *
     * @param shelfName GoodReads shelf name
     * @param grBookId  GoodReads book id
     *
     * @return reviewId
     *
     * @throws AuthorizationException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book?
     * @throws IOException            on other failures
     */
    public long add(@NonNull final String shelfName,
                    final long grBookId)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        return doCall(shelfName, grBookId, false);
    }

    /**
     * Remove the passed book from the passed shelf.
     *
     * @param shelfName GoodReads shelf name
     * @param grBookId  GoodReads book id
     *
     * @throws AuthorizationException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book?
     * @throws IOException            on other failures
     */
    public void remove(@NonNull final String shelfName,
                       final long grBookId)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        doCall(shelfName, grBookId, true);
    }

    /**
     * Do the main work; same API call for add & remove.
     *
     * @param shelfName GoodReads shelf name
     * @param grBookId  GoodReads book id
     * @param isRemove  {@code true} for 'remove', {@code false} for 'add'
     *
     * @return reviewId
     *
     * @throws AuthorizationException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book?
     * @throws IOException            on other failures
     */
    private long doCall(@NonNull final String shelfName,
                        final long grBookId,
                        final boolean isRemove)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        mReviewId = 0;

        String url = GoodreadsManager.BASE_URL + "/shelf/add_to_shelf.xml";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("book_id", String.valueOf(grBookId));
        parameters.put("name", shelfName);
        if (isRemove) {
            parameters.put("a", "remove");
        }

        // Use a parser based on the filters
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        // Send call. Errors will result in an exception.
        mManager.executePost(url, parameters, handler, true);

        return mReviewId;
    }

    /**
     * Build filters to process typical output.
     */
    private void buildFilters() {
        // We only care about review-id:
        XmlFilter.buildFilter(mRootFilter, XML_SHELF, XML_REVIEW_ID)
                 .setEndAction(mHandleReviewId);
    }
}
