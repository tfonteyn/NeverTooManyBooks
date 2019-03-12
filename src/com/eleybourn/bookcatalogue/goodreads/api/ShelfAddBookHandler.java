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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import com.eleybourn.bookcatalogue.goodreads.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.xml.ElementContext;
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
public class ShelfAddBookHandler
        extends ApiHandler {

    private long mReviewId;
    private final XmlHandler mHandleReviewId = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {
            try {
                mReviewId = Long.parseLong(context.getBody());
            } catch (NumberFormatException ignore) {
            }
        }
    };

    /**
     * Constructor.
     */
    public ShelfAddBookHandler(@NonNull final GoodreadsManager manager) {
        super(manager);
        buildFilters();
    }

    /**
     * Add the passed book to the passed shelf.
     *
     * @return reviewId
     */
    public long add(@NonNull final String shelfName,
                    final long grBookId)
            throws IOException,
                   AuthorizationException,
                   BookNotFoundException {

        return doCall(shelfName, grBookId, false);
    }

    /**
     * Remove the passed book from the passed shelf.
     */
    public void remove(@NonNull final String shelfName,
                       final long grBookId)
            throws IOException,
                   AuthorizationException,
                   BookNotFoundException {

        doCall(shelfName, grBookId, true);
    }

    /**
     * Do the main work; same API call for add & remove.
     *
     * @return reviewId
     */
    private long doCall(@NonNull final String shelfName,
                        final long grBookId,
                        final boolean isRemove)
            throws IOException,
                   AuthorizationException,
                   BookNotFoundException {

        mReviewId = 0;

        HttpPost post = new HttpPost(GoodreadsManager.BASE_URL + "/shelf/add_to_shelf.xml");

        ArrayList<NameValuePair> parameters = new ArrayList<>();
        if (isRemove) {
            parameters.add(new BasicNameValuePair("a", "remove"));
        }
        parameters.add(new BasicNameValuePair("book_id", String.valueOf(grBookId)));
        parameters.add(new BasicNameValuePair("name", shelfName));

        post.setEntity(new UrlEncodedFormEntity(parameters, StandardCharsets.UTF_8));

        // Use a parser based on the filters
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        // Send call. Errors will result in an exception.
        mManager.execute(post, handler, true);

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
