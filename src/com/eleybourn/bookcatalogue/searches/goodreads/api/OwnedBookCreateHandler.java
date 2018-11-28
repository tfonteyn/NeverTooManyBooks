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

package com.eleybourn.bookcatalogue.searches.goodreads.api;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NetworkException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.utils.RTE;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 * TODO: OwnedBookCreateHandler WORK IN PROGRESS
 * 
 * @author Philip Warner
 */
@SuppressWarnings("unused")
public class OwnedBookCreateHandler extends ApiHandler {

	//public enum ConditionCode {
	//	BRAND_NEW, LIKE_NEW, VERY_GOOD, GOOD, ACCEPTABLE, POOR
	//}

	public OwnedBookCreateHandler(final @NonNull GoodreadsManager manager) {
		super(manager);
	}

	/*
	 * <owned-book>
	 *  <available-for-swap type='boolean'>false</available-for-swap>
	 *  <book-id type='integer'>9376943</book-id>
	 *  <book-trades-count type='integer'>0</book-trades-count>
	 *  <comments-count type='integer'>0</comments-count>
	 *  <condition-code type='integer' nil='true'></condition-code>
	 *  <condition-description nil='true'></condition-description>
	 *  <created-at type='datetime'>2012-01-01T07:08:47-08:00</created-at>
	 *  <current-owner-id type='integer'>5129458</current-owner-id>
	 *  <current-owner-name nil='true'></current-owner-name>
	 *  <id type='integer'>5431803</id>
	 *  <last-comment-at type='datetime' nil='true'></last-comment-at>
	 *  <original-purchase-date type='datetime' nil='true'></original-purchase-date>
	 *  <original-purchase-location nil='true'></original-purchase-location>
	 *  <review-id type='integer' nil='true'></review-id>
	 *  <swappable-flag type='boolean'>false</swappable-flag>
	 *  <unique-code nil='true'></unique-code>
	 *  <updated-at type='datetime'>2012-01-01T07:08:47-08:00</updated-at>
	 *  <work-id type='integer'>14260549</work-id>
	 * </owned-book>
	 */
	private class OwnedBookCreateParser extends DefaultHandler {
		private static final String BOOK_ID = "book-id";
		private static final String OWNED_BOOK_ID = "id";
		private static final String WORK_ID = "work-id";

		final StringBuilder mBuilder = new StringBuilder();
		int mBookId = 0;
		//int mOwnedBookId = 0;
		//int mWorkId = 0;

		@Override
		@CallSuper
		public void characters(final @NonNull char[] ch, final int start, final int length) throws SAXException {
			super.characters(ch, start, length);
			mBuilder.append(ch, start, length);
		}

		public int getBookId() {
			return mBookId;
		}

		//public int getOwnedBookId() {
		//	return mOwnedBookId;
		//}
		//
		//public int getWorkId() {
		//	return mWorkId;
		//}

		@Override
		@CallSuper
		public void startElement(final @NonNull String uri, final @NonNull String localName, final @NonNull String name, final @NonNull Attributes attributes) throws SAXException {
			super.startElement(uri, localName, name, attributes);

			// reset the string. See note in endElement() for a discussion.
			mBuilder.setLength(0);

		}

		@Override
		@CallSuper
		public void endElement(final @NonNull String uri, final @NonNull String localName, final @NonNull String name) throws SAXException {
			super.endElement(uri, localName, name);

			if (localName.equalsIgnoreCase(BOOK_ID)) {
				mBookId = Integer.parseInt( mBuilder.toString() );
			}
			//else if (localName.equalsIgnoreCase(OWNED_BOOK_ID)) {
				//mOwnedBookId = Integer.parseInt( mBuilder.toString() );
			//} else if (localName.equalsIgnoreCase(WORK_ID)) {
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
	
	/* 
	 *  URL: http://www.goodreads.com/owned_books.xml
	 *	HTTP method: POST
	 *	Parameters:
	 *	    owned_book[condition_code]: one of 10 (brand new), 20 (like new), 30 (very good), 40 (good), 50 (acceptable), 60 (poor)
	 *	    owned_book[unique_code]: BookCrossing id (BCID)
	 *	    owned_book[original_purchase_location]: where this book was acquired
	 *	    owned_book[book_id]: id of the book (required)
	 *	    owned_book[original_purchase_date]: when book was acquired
	 *	    owned_book[condition_description]: description of book's condition
	 *	    owned_book[available_for_swap]: true or false, if book is available for swap
	 */
	public void create(final @NonNull String isbn, final @NonNull List<String> shelves)
			throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, 
					NotAuthorizedException, NetworkException, BookNotFoundException 
	{
		IsbnToId isbnToId = new IsbnToId(mManager);
		long id;
		
		try {
			id = isbnToId.isbnToId(isbn);
		} catch (BookNotFoundException e) {
			throw new RTE.IsbnInvalidException(e);
		}

		HttpPost post = new HttpPost(GoodreadsManager.BASE_URL + "/owned_books.xml");

		List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("owned_book[book_id]", Long.toString(id)));
        
        post.setEntity(new UrlEncodedFormEntity(parameters));	        	

        OwnedBookCreateParser handler = new OwnedBookCreateParser();
        mManager.execute(post, handler, true);

        ShelfAddBookHandler shelfAdd = new ShelfAddBookHandler(mManager);
        for( String shelf : shelves) {
	        shelfAdd.add(shelf, handler.getBookId());	
        }
	}

	public void create(final @NonNull String isbn, final @NonNull String shelf)
			throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException, 
					NotAuthorizedException, NetworkException, BookNotFoundException {
		List<String> shelves = new ArrayList<>();
		shelves.add(shelf);
		this.create(isbn, shelves);
	}
}
