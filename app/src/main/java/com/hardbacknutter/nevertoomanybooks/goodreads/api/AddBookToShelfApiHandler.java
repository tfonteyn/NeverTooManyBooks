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
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter.XmlHandler;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * shelves.add_to_shelf   —   Add (or remove) a book to a shelf.
 *
 * <a href="https://www.goodreads.com/api/index#shelves.add_to_shelf">shelves.add_to_shelf</a>
 * <p>
 * shelves.add_books_to_shelves   —   Add books to many shelves.
 *
 * <a href="https://www.goodreads.com/api/index#shelves.add_books_to_shelves">
 * shelves.add_books_to_shelves</a>
 * <p>
 * ENHANCE: Parse the result and store it against the bookshelf in the database.
 * Currently, this is not a simple thing to do because bookshelf naming rules in
 * Goodreads are much more restrictive: no spaces, punctuation (at least).
 * <p>
 * Need to add the following to bookshelf table:
 * - gr_bookshelf_id
 * - (perhaps) gr_bookshelf_name
 */
public class AddBookToShelfApiHandler
        extends ApiHandler {

    /** Add one book to one shelf (or remove it). */
    private static final String URL_1_1 =
            GoodreadsManager.BASE_URL + "/shelf/add_to_shelf.xml";

    /** Add multiple books to multiple shelves. */
    private static final String URL_X_X =
            GoodreadsManager.BASE_URL + "/shelf/add_books_to_shelves.xml";

    /** Resulting review-id after the request. */
    private long mReviewId;
    /** Handler for the review-id. */
    private final XmlHandler mHandleReviewId = elementContext -> {
        try {
            mReviewId = Long.parseLong(elementContext.getBody());
        } catch (@NonNull final NumberFormatException ignore) {
            mReviewId = 0;
        }
    };

    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    private final XmlFilter mRootFilter = new XmlFilter("");

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     *
     * @throws CredentialsException with GoodReads
     */
    public AddBookToShelfApiHandler(@NonNull final Context appContext,
                                    @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(appContext, grAuth);
        mGrAuth.hasValidCredentialsOrThrow(appContext);

        buildFilters();
    }

    /**
     * Add the passed book to the passed shelves.
     * <p>
     * We deliberately limit this call to a single book for now.
     *
     * @param grBookId   GoodReads book id
     * @param shelfNames list of GoodReads shelf names
     *
     * @return reviewId
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception    the requested item was not found
     * @throws IOException          on other failures
     */
    public long add(final long grBookId,
                    @NonNull final Iterable<String> shelfNames)
            throws CredentialsException, Http404Exception, IOException {

        final String shelves = TextUtils.join(",", shelfNames);

        mReviewId = 0;
        final Map<String, String> parameters = new HashMap<>();
        parameters.put("bookids", String.valueOf(grBookId));
        parameters.put("shelves", shelves);

        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executePost(URL_X_X, parameters, true, handler);

        return mReviewId;
    }

    /**
     * Add the passed book to the passed shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @return reviewId
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception    the requested item was not found
     * @throws IOException          on other failures
     */
    public long add(final long grBookId,
                    @NonNull final String shelfName)
            throws CredentialsException, Http404Exception, IOException {

        return send(grBookId, shelfName, false);
    }

    /**
     * Remove the passed book from the passed shelf.
     * <p>
     * Exclusive shelves: to-read, currently-reading, read:
     * Goodreads cannot remove books from the exclusive shelves.
     * You can move a book from one shelf to another.
     * <p>
     * Non-exclusive shelves, i.e. user-shelves:
     * Book can be removed from the shelf, but it will end up on the "read" shelf.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception    the requested item was not found
     * @throws IOException          on other failures
     */
    public void remove(final long grBookId,
                       @NonNull final String shelfName)
            throws CredentialsException, Http404Exception, IOException {

        send(grBookId, shelfName, true);
    }

    /**
     * Single book and single shelf; same call for add & remove.
     *
     * @param grBookId  GoodReads book id
     * @param shelfName GoodReads shelf name
     * @param isRemove  {@code true} for 'remove', {@code false} for 'add'
     *
     * @return reviewId
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception    the requested item was not found
     * @throws IOException          on other failures
     */
    private long send(final long grBookId,
                      @NonNull final String shelfName,
                      final boolean isRemove)
            throws CredentialsException, Http404Exception, IOException {

        mReviewId = 0;
        Map<String, String> parameters = new HashMap<>();
        parameters.put("book_id", String.valueOf(grBookId));
        parameters.put("name", shelfName);
        if (isRemove) {
            parameters.put("a", "remove");
        }

        final DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executePost(URL_1_1, parameters, true, handler);

        return mReviewId;
    }

    /**
     * Build filters to process typical output.
     * <p>
     * Typical response when adding to a single shelf:
     * <pre>
     * {@code
     * <?xml version="1.0"?>
     * <shelf>
     *     <id type="integer">2562434578</id>
     *     <review-id type="integer">2916142681</review-id>
     *     <updated-at type="datetime">2019-07-29T21:35:06+00:00</updated-at>
     *     <created-at type="datetime">2019-07-29T21:35:06+00:00</created-at>
     *     <position type="integer">12</position>
     *     <user-shelf-id type="integer">326549924</user-shelf-id>
     *     <name>books</name>
     *     <exclusive type="boolean">false</exclusive>
     * </shelf>
     * }
     * </pre>
     * <p>
     * Typical response when adding to multiple shelves:
     * <pre>
     * {@code
     *  <?xml version="1.0"?>
     *   <GoodreadsResponse>
     *     <Request>
     *         ...
     *     </Request>
     *     <reviews>
     *       <my_review>
     *          <id>2916150810</id>
     *          <book_id>8498055</book_id>
     *          <rating>0</rating>
     *          <updated_at>Mon Jul 29 14:42:01 -0700 2019</updated_at>
     *          <shelves>
     *              <shelf name="read" exclusive="true" id="277841466" review_shelf_id="" />
     *              <shelf exclusive="false" id="326549924" name="books"
     *                     review_shelf_id="2562442400" sortable="false" />
     *          </shelves>
     *          <started_at></started_at>
     *          <read_at></read_at>
     *          <date_added>Mon Jul 29 14:42:01 -0700 2019</date_added>
     *       </my_review>
     *     </reviews>
     *  </GoodreadsResponse>
     *     }
     * </pre>
     * <p>
     * We only care about review-id:
     */
    private void buildFilters() {
        // add to single shelf
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_SHELF, XmlTags.XML_REVIEW_ID)
                 .setEndAction(mHandleReviewId);
        // add to multiple shelves
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE,
                              XmlTags.XML_REVIEWS, XmlTags.XML_MY_REVIEW, XmlTags.XML_ID)
                 .setEndAction(mHandleReviewId);
    }
}
