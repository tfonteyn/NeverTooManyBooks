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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsShelf;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.CredentialsException;
import com.eleybourn.bookcatalogue.utils.BookNotFoundException;

/**
 * review.edit   —   Edit a review.
 *
 * <a href="https://www.goodreads.com/api/index#review.edit">
 *     https://www.goodreads.com/api/index#review.edit</a>
 */
public class ReviewUpdateApiHandler
        extends ApiHandler {

    /**
     * Parameters.
     *
     * 1: review id
     */
    private static final String URL = GoodreadsManager.BASE_URL + "/review/%1$s.xml";

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     *
     * @throws CredentialsException with GoodReads
     */
    public ReviewUpdateApiHandler(@NonNull final GoodreadsManager grManager)
            throws CredentialsException {
        super(grManager);
        if (!grManager.hasValidCredentials()) {
            throw new CredentialsException(R.string.goodreads);
        }

        // buildFilters();
    }

    /**
     * Update a fixed set of fields; see method parameters.
     * <p>
     * Depending on the state of 'isRead', the date fields will determine which shelf is used.
     *
     * @param reviewId        to update
     * @param finishedReading Flag to indicate we finished reading this book.
     * @param readStart       (optional) Date when we started reading this book, YYYY-MM-DD format
     * @param readEnd         (optional) Date when we finished reading this book, YYYY-MM-DD format
     * @param rating          Rating 0-5 with 0 == No rating
     * @param review          (optional) Text for the review
     *
     * @throws CredentialsException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book or the ISBN was invalid.
     * @throws IOException            on other failures
     */
    public void update(final long reviewId,
                       final boolean finishedReading,
                       @Nullable final String readStart,
                       @Nullable final String readEnd,
                       @IntRange(from = 0, to = 5)
                       final int rating,
                       @Nullable final String review)
            throws CredentialsException,
                   BookNotFoundException,
                   IOException {

        String url = String.format(URL, reviewId);
        Map<String, String> parameters = new HashMap<>();

        // hardcoded shelf names, see API docs in class header.
        if (finishedReading) {
            parameters.put("shelf", GoodreadsShelf.VIRTUAL_READ);
            parameters.put("finished", "true");
            if (readEnd != null && !readEnd.isEmpty()) {
                parameters.put("review[read_at]", readEnd);
            }
        } else {
            if (readStart != null && !readStart.isEmpty()) {
                parameters.put("shelf", GoodreadsShelf.VIRTUAL_CURRENTLY_READING);
                parameters.put("review[read_at]", readStart);
            } else {
                parameters.put("shelf", GoodreadsShelf.VIRTUAL_TO_READ);
            }
        }

        if (rating >= 0) {
            parameters.put("review[rating]", String.valueOf(rating));
        }

        if (review != null) {
            parameters.put("review[review]", review);
        }

        //XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        executePost(url, parameters, null, true);
    }

    /*
     * Build filters to process typical output.
     *
     * Typical response can be ignored, but is:
     * <pre>
     *     {@code
     *       <review>
     *           <book-id type='integer'>375802</book-id>
     *           <comments-count type='integer'>0</comments-count>
     *           <created-at type='datetime'>2011-03-15T01:51:42-07:00</created-at>
     *           <hidden-flag type='boolean'>false</hidden-flag>
     *           <id type='integer'>154477749</id>
     *           <language-code type='integer' nil='true'></language-code>
     *           <last-comment-at type='datetime' nil='true'></last-comment-at>
     *           <last-revision-at type='datetime'>2012-01-01T05:43:30-08:00</last-revision-at>
     *           <non-friends-rating-count type='integer'>0</non-friends-rating-count>
     *           <notes></notes>
     *           <rating type='integer'>4</rating>
     *           <ratings-count type='integer'>0</ratings-count>
     *           <ratings-sum type='integer'>0</ratings-sum>
     *           <read-at type='datetime'>1991-05-01T00:00:00-07:00</read-at>
     *           <read-count></read-count>
     *           <read-status>read</read-status>
     *           <recommendation></recommendation>
     *           <recommender-user-id1 type='integer'>0</recommender-user-id1>
     *           <recommender-user-name1></recommender-user-name1>
     *           <review></review>
     *           <sell-flag type='boolean'>true</sell-flag>
     *           <spoiler-flag type='boolean'>false</spoiler-flag>
     *           <started-at type='datetime' nil='true'></started-at>
     *           <updated-at type='datetime'>2012-01-01T05:43:30-08:00</updated-at>
     *           <user-id type='integer'>5129458</user-id>
     *           <weight type='integer'>0</weight>
     *           <work-id type='integer'>2422333</work-id>
     *       </review>
     *      }
     * </pre>
     */
//    private void buildFilters() {
//        // We only care about book-id:
//        XmlFilter.buildFilter(mRootFilter, XML_REVIEW, "book-id")
//                 .setEndAction(mHandleBookId);
//    }
}
