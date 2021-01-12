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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsShelf;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlDumpParser;

/**
 * review.edit   —   Edit a review.
 *
 * <a href="https://www.goodreads.com/api/index#review.edit">review.edit</a>
 * <p>
 * The response actually contains the private notes; but there seems to be no way to *send* them.
 */
public class ReviewEditApiHandler
        extends ApiHandler {

    /**
     * Parameters.
     * <p>
     * 1: review id
     */
    private static final String URL = GoodreadsManager.BASE_URL + "/review/%1$s.xml";

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException if there are no valid credentials available
     */
    public ReviewEditApiHandler(@NonNull final Context appContext,
                                @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);
        mGrAuth.hasValidCredentialsOrThrow(appContext);

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
     *                        //@param privateNotes    (optional) Text for the Goodreads PRIVATE notes
     * @param review          (optional) Text for the review, PUBLIC
     *
     * @throws IOException on failures
     */
    public void update(final long reviewId,
                       final boolean finishedReading,
                       @Nullable final String readStart,
                       @Nullable final String readEnd,
                       @IntRange(from = 0, to = 5) final int rating,
                       //@Nullable final String privateNotes,
                       @Nullable final String review)
            throws GeneralParsingException, IOException {

        final String url = String.format(URL, reviewId);
        final Map<String, String> parameters = new HashMap<>();

        parameters.put("id", String.valueOf(reviewId));

        // hardcoded shelf names, see docs in class header.
        if (finishedReading) {
            parameters.put("finished", "true");
            parameters.put("shelf", GoodreadsShelf.VIRTUAL_READ);
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

        // Do NOT sync Private Notes <-> Review.
        if (review != null) {
            parameters.put("review[review]", review);
        }
        //ENHANCE: see if we 'somehow' can sync our personal notes with the site private notes.
        // This was a pure guess... and it was rejected by the site.
//        if (privateNotes != null) {
//            parameters.put("review[notes]", review);
//        }

        DefaultHandler handler = null;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.GOODREADS_HTTP_XML) {
            handler = new XmlDumpParser();
        }
        executePost(url, parameters, true, handler);
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
     *           <notes>these are the private notes</notes>
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
//    }
}
