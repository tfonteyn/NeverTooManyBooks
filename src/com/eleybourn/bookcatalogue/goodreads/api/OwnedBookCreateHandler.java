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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.eleybourn.bookcatalogue.goodreads.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.AuthorizationException;
import com.eleybourn.bookcatalogue.utils.ISBN;

/**
 * TODO: OwnedBookCreateHandler WORK IN PROGRESS.
 * <p>
 * Typical response.
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
 *
 * @author Philip Warner
 */
@SuppressWarnings("unused")
public class OwnedBookCreateHandler
        extends ApiHandler {

    //public enum ConditionCode {
    //  BRAND_NEW, LIKE_NEW, VERY_GOOD, GOOD, ACCEPTABLE, POOR
    //}

    public OwnedBookCreateHandler(@NonNull final GoodreadsManager manager) {
        super(manager);
    }

    /*
     * URL: http://www.goodreads.com/owned_books.xml
     * HTTP method: POST
     * Parameters:
     *    owned_book[condition_code]: one of 10 (brand new), 20 (like new), 30 (very good),
     *    40 (good), 50 (acceptable), 60 (poor)
     *    owned_book[unique_code]: BookCrossing id (BCID)
     *    owned_book[original_purchase_location]: where this book was acquired
     *    owned_book[book_id]: id of the book (required)
     *    owned_book[original_purchase_date]: when book was acquired
     *    owned_book[condition_description]: description of book's condition
     *    owned_book[available_for_swap]: true or false, if book is available for swap
     */
    public void create(@NonNull final String isbn,
                       @NonNull final List<String> shelves)
            throws IOException,
                   AuthorizationException,
                   BookNotFoundException {
        IsbnToId isbnToId = new IsbnToId(mManager);
        long id;

        try {
            id = isbnToId.isbnToId(isbn);
        } catch (BookNotFoundException e) {
            throw new ISBN.IsbnInvalidException(e);
        }

        HttpPost post = new HttpPost(GoodreadsManager.BASE_URL + "/owned_books.xml");

        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("owned_book[book_id]", String.valueOf(id)));

        post.setEntity(new UrlEncodedFormEntity(parameters));

        OwnedBookCreateParser handler = new OwnedBookCreateParser();
        mManager.execute(post, handler, true);

        ShelfAddBookHandler shelfAdd = new ShelfAddBookHandler(mManager);
        for (String shelf : shelves) {
            shelfAdd.add(shelf, handler.getBookId());
        }
    }

    public void create(@NonNull final String isbn,
                       @NonNull final String shelf)
            throws IOException,
                   AuthorizationException,
                   BookNotFoundException {
        List<String> shelves = new ArrayList<>();
        shelves.add(shelf);
        create(isbn, shelves);
    }

    /**

     */
    private static class OwnedBookCreateParser
            extends DefaultHandler {

        private static final String XML_BOOK_ID = "book-id";
        private static final String XML_OWNED_BOOK_ID = "id";
        private static final String XML_WORK_ID = "work-id";

        private final StringBuilder mBuilder = new StringBuilder();
        private int mBookId;
        //private int mOwnedBookId;
        //private int mWorkId;

        @Override
        @CallSuper
        public void characters(@NonNull final char[] ch,
                               final int start,
                               final int length)
                throws SAXException {
            super.characters(ch, start, length);
            mBuilder.append(ch, start, length);
        }

        public int getBookId() {
            return mBookId;
        }

        //public int getOwnedBookId() {
        //  return mOwnedBookId;
        //}
        //
        //public int getWorkId() {
        //  return mWorkId;
        //}

        @Override
        @CallSuper
        public void startElement(@NonNull final String uri,
                                 @NonNull final String localName,
                                 @NonNull final String qName,
                                 @NonNull final Attributes attributes)
                throws SAXException {
            super.startElement(uri, localName, qName, attributes);

            // reset the string. See note in endElement() for a discussion.
            mBuilder.setLength(0);

        }

        @Override
        @CallSuper
        public void endElement(@NonNull final String uri,
                               @NonNull final String localName,
                               @NonNull final String qName)
                throws SAXException, NumberFormatException {
            super.endElement(uri, localName, qName);

            if (localName.equalsIgnoreCase(XML_BOOK_ID)) {
                mBookId = Integer.parseInt(mBuilder.toString());
            }
            //else if (localName.equalsIgnoreCase(XML_OWNED_BOOK_ID)) {
            //mOwnedBookId = Integer.parseInt( mBuilder.toString() );
            //} else if (localName.equalsIgnoreCase(XML_WORK_ID)) {
            //mWorkId = Integer.parseInt( mBuilder.toString() );
            //}

            // Note:
            // Always reset the length. This is not entirely the right thing to do, but works
            // because we always want strings from the lowest level (leaf) XML elements.
            // To be completely correct, we should maintain a stack of builders that are pushed and
            // popped as each startElement/endElement is called. But lets not be pedantic for now.
            mBuilder.setLength(0);
        }
    }
}
