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
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;

/**
 * TODO: ReviewUpdateApiHandler WORK IN PROGRESS.
 *
 * @author Philip Warner
 */
public class ReviewUpdateApiHandler
        extends ApiHandler {

    /**
     * Constructor.
     *
     * @param grManager the Goodreads Manager
     */
    public ReviewUpdateApiHandler(@NonNull final GoodreadsManager grManager) {
        super(grManager);
    }

    /**
     *
     * @throws AuthorizationException with GoodReads
     * @throws BookNotFoundException  GoodReads does not have the book?
     * @throws IOException            on other failures
     */
    public void update(final long reviewId,
                       final boolean isRead,
                       @Nullable final String readAt,
                       @Nullable final String review,
                       final int rating)
            throws AuthorizationException,
                   BookNotFoundException,
                   IOException {

        String url = GoodreadsManager.BASE_URL + "/review/" + reviewId + ".xml";
        Map<String, String> parameters = new HashMap<>();

//        StringBuilder shelvesString = null;
//        if (shelves != null && !shelves.isEmpty()) {
//            shelvesString = new StringBuilder();
//            if (!shelves.isEmpty()) {
//                shelvesString.append(shelves.get(0));
//            }
//            for (int i = 1; i < shelves.size(); i++) {
//                shelvesString.append(',').append(shelves.get(i));
//            }
//        }
//        if (shelvesString != null) {
//            parameters.put("shelf", shelvesString.toString());
//        }

        // Set the 'read' or 'to-read' shelf based on status.
        // Note a lot of point...it does not update goodreads!
        if (isRead) {
            parameters.put("shelf", "read");
        } else {
            parameters.put("shelf", "to-read");
        }

        if (review != null) {
            parameters.put("review[review]", review);
        }

        if (readAt != null && !readAt.isEmpty()) {
            parameters.put("review[read_at]", readAt);
        }

        if (rating >= 0) {
            parameters.put("review[rating]", String.valueOf(rating));
        }

        //ReviewUpdateParser handler = new ReviewUpdateParser();
        mManager.executePost(url, parameters, null, true);
        //String s = handler.getHtml();
        //Logger.info(s);
        /* Typical response can be ignored, but is:
            <review>
                <book-id type='integer'>375802</book-id>
                <comments-count type='integer'>0</comments-count>
                <created-at type='datetime'>2011-03-15T01:51:42-07:00</created-at>
                <hidden-flag type='boolean'>false</hidden-flag>
                <id type='integer'>154477749</id>
                <language-code type='integer' nil='true'></language-code>
                <last-comment-at type='datetime' nil='true'></last-comment-at>
                <last-revision-at type='datetime'>2012-01-01T05:43:30-08:00</last-revision-at>
                <non-friends-rating-count type='integer'>0</non-friends-rating-count>
                <notes></notes>
                <rating type='integer'>4</rating>
                <ratings-count type='integer'>0</ratings-count>
                <ratings-sum type='integer'>0</ratings-sum>
                <read-at type='datetime'>1991-05-01T00:00:00-07:00</read-at>
                <read-count></read-count>
                <read-status>read</read-status>
                <recommendation></recommendation>
                <recommender-user-id1 type='integer'>0</recommender-user-id1>
                <recommender-user-name1></recommender-user-name1>
                <review></review>
                <sell-flag type='boolean'>true</sell-flag>
                <spoiler-flag type='boolean'>false</spoiler-flag>
                <started-at type='datetime' nil='true'></started-at>
                <updated-at type='datetime'>2012-01-01T05:43:30-08:00</updated-at>
                <user-id type='integer'>5129458</user-id>
                <weight type='integer'>0</weight>
                <work-id type='integer'>2422333</work-id>
            </review>
         */
    }

}
