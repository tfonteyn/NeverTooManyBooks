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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * TODO: OwnedBookCreateApiHandler WORK IN PROGRESS.
 * <p>
 * owned_books.create   —   Add to books owned.
 *
 * <a href="https://www.goodreads.com/api/index#owned_books.create">owned_books.create</a>
 */
@SuppressWarnings("unused")
class OwnedBookCreateApiHandler
        extends com.hardbacknutter.nevertoomanybooks.goodreads.api.ApiHandler {

    private static final String URL = GoodreadsHandler.BASE_URL + "/owned_books.xml";

    /**
     * Constructor.
     *
     * @param context Current context
     * @param grAuth  Authentication handler
     *
     * @throws CredentialsException with GoodReads
     */
    public OwnedBookCreateApiHandler(@NonNull final Context context,
                                     @NonNull final GoodreadsAuth grAuth)
            throws CredentialsException {
        super(grAuth);
        mGoodreadsAuth.hasValidCredentialsOrThrow(context);

        // buildFilters();
    }


    /**
     * @param context      Current context
     * @param isbn         ISBN to use, must be valid
     * @param dateAcquired (optional)
     *
     * @return the Goodreads book ID
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    public long create(@NonNull final Context context,
                       @NonNull final ISBN isbn,
                       @Nullable final String dateAcquired)
            throws CredentialsException, Http404Exception, IOException {

        IsbnToIdApiHandler isbnToIdApiHandler = new IsbnToIdApiHandler(context, mGoodreadsAuth);
        long grBookId = isbnToIdApiHandler.isbnToId(isbn.asText());
        create(grBookId, dateAcquired);
        return grBookId;
    }

    /**
     * URL: http://www.goodreads.com/owned_books.xml<br>
     * HTTP method: POST
     * <ul>Parameters:
     * <li>owned_book[book_id]: id of the book (required)</li>
     * <li>owned_book[condition_code]: one of 10 (brand new), 20 (like new),<br>
     * 30 (very good), 40 (good), 50 (acceptable), 60 (poor)</li>
     * <li>owned_book[condition_description]: description of book's condition</li>
     * <li>owned_book[original_purchase_date]: when book was purchased</li>
     * <li>owned_book[original_purchase_location]: where this book was purchased</li>
     * <li>owned_book[unique_code]: BookCrossing id (BCID)</li>
     * </ul>
     *
     * @param grBookId     Goodreads book id
     * @param dateAcquired (optional)
     *
     * @throws CredentialsException with GoodReads
     * @throws Http404Exception     the requested item was not found
     * @throws IOException          on other failures
     */
    public void create(final long grBookId,
                       @Nullable final String dateAcquired)
            throws CredentialsException, Http404Exception, IOException {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("owned_book[book_id]", String.valueOf(grBookId));
        if (dateAcquired != null) {
            parameters.put("owned_book[original_purchase_date]", dateAcquired);
        }

        //DefaultHandler handler = new XmlResponseParser(mRootFilter);
        executePost(URL, parameters, true, null);
    }

    /*
     * Build filters to process typical output.
     *
     * Typical response:
     * <pre>
     *  {@code
     *  <owned-book>
     *    <available-for-swap type='boolean'>false</available-for-swap>
     *    <book-id type='integer'>9376943</book-id>
     *    <book-trades-count type='integer'>0</book-trades-count>
     *    <comments-count type='integer'>0</comments-count>
     *    <condition-code type='integer' nil='true'></condition-code>
     *    <condition-description nil='true'></condition-description>
     *    <created-at type='datetime'>2012-01-01T07:08:47-08:00</created-at>
     *    <current-owner-id type='integer'>5129458</current-owner-id>
     *    <current-owner-name nil='true'></current-owner-name>
     *    <id type='integer'>5431803</id>
     *    <last-comment-at type='datetime' nil='true'></last-comment-at>
     *    <original-purchase-date type='datetime' nil='true'></original-purchase-date>
     *    <original-purchase-location nil='true'></original-purchase-location>
     *    <review-id type='integer' nil='true'></review-id>
     *    <swappable-flag type='boolean'>false</swappable-flag>
     *    <unique-code nil='true'></unique-code>
     *    <updated-at type='datetime'>2012-01-01T07:08:47-08:00</updated-at>
     *    <work-id type='integer'>14260549</work-id>
     *  </owned-book>
     *  }
     * </pre>
     */
//    private void buildFilters() {
//        // We only care about book-id:
//        XmlFilter.buildFilter(mRootFilter, XML_REVIEW, "book-id")
//                 .setEndAction(mHandleBookId);
//    }
}
