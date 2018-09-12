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

package com.eleybourn.bookcatalogue.searches.goodreads;

import android.content.Context;

import com.eleybourn.bookcatalogue.utils.BCQueueManager;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookEvents.GrNoIsbnEvent;
import com.eleybourn.bookcatalogue.BookEvents.GrNoMatchEvent;
import com.eleybourn.bookcatalogue.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.BooksRowView;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.ExportDisposition;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

import net.philipwarner.taskqueue.QueueManager;

/**
 * Background task class to send all books in the database to goodreads.
 * 
 * @author Philip Warner
 */
public class SendAllBooksTask extends GenericTask {
	private static final long serialVersionUID = -1933000305276643875L;

	/** Last book ID processed */
	private long mLastId = 0;
	/** Flag indicating if it should only send UPDATED books to goodreads; false == all books */
	private final boolean mUpdatesOnly;
	
	/** Number of books with no ISBN */
	private int mNoIsbn = 0;
	/** Number of books that had ISBN but could not be found */
	private int mNotFound = 0;
	/** Number of books successfully sent */
	private int mSent = 0;
	/** Total count of books processed */
	private int mCount = 0;
	/** Total count of books that are in cursor */
	private int mTotalBooks = 0;

	/**
	 * Constructor
	 */
	SendAllBooksTask(boolean updatesOnly) {
		super(BookCatalogueApp.getResourceString(R.string.send_books_to_goodreads));
		mUpdatesOnly = updatesOnly;
	}

	/**
	 * Run the task, log exceptions.
	 */
	@Override
	public boolean run(QueueManager manager, Context c) {
		boolean result = false;
		try {
			result = sendAllBooks(manager, c);			
		} catch (Exception e) {
			Logger.logError(e, "Error sending books to GoodReads");
		}
		return result;
	}

	/**
	 * Do the mean of the task. Deal with restarts by using mLastId as starting point.
	 */
	private boolean sendAllBooks(QueueManager queueManager, Context context) throws NotAuthorizedException {
		//int lastSave = mCount;
		boolean needsRetryReset = true;

		// TODO: Work out a way of checking if GR site is up
		//if (!Utils.hostIsAvailable(context, "www.goodreads.com"))
		//	return false;

		if (!Utils.isNetworkAvailable(context)) {
			// Only wait 5 mins max on network errors.
			if (getRetryDelay() > 300)
				setRetryDelay(300);
			return false;
		}

		// Get the app context; the underlying activity may go away. And get DB.
		GoodreadsManager grManager = new GoodreadsManager();
		Context ctx = context.getApplicationContext();
		
		CatalogueDBAdapter  db = new CatalogueDBAdapter(ctx.getApplicationContext());

		// Ensure we are allowed
		if (!grManager.hasValidCredentials()) {
			throw new NotAuthorizedException(null);
		}

		db.open();
		BooksCursor books = null;

		try {
			books = db.getAllBooksForGoodreadsCursor(mLastId, mUpdatesOnly);
			final BooksRowView book = books.getRowView();
			mTotalBooks = books.getCount() + mCount;

			while (books.moveToNext()) {

				// Try to export one book
				ExportDisposition disposition;
				Exception exportException = null;
				try {
					disposition = grManager.sendOneBook(db, book);
				} catch (Exception e) {
					disposition = ExportDisposition.error;
					exportException = e;
				}

				// Handle the result
				switch(disposition) {
				case error:
					this.setException(exportException);
					queueManager.saveTask(this);
					return false;
				case sent:
					// Record the change
					db.setGoodreadsSyncDate(books.getId());
					mSent++;
					break;
				case noIsbn:
					storeEvent( new GrNoIsbnEvent(book.getId()) );
					mNoIsbn++;
					break;
				case notFound:
					storeEvent( new GrNoMatchEvent(book.getId()) );
					mNotFound++;
					break;
				case networkError:
					// Only wait 5 mins on network errors.
					if (getRetryDelay() > 300)
						setRetryDelay(300);						
					queueManager.saveTask(this);
					return false;
				}

				// Update internal status
				mCount++;
				mLastId = books.getId();
				// If we have done one successfully, reset the counter so a subsequent network error does not result in a long delay
				if (needsRetryReset) {
					needsRetryReset = false;
					resetRetryCounter();
				}

				// Save every few rows in case phone dies (and to allow task queries to see data)
				// Actually, save every row because it updates the UI, and sending a row takes a while.
				//if (mCount - lastSave >= 5) {
				//	qmanager.saveTask(this);
				//	lastSave = mCount;
				//}
				queueManager.saveTask(this);

				if (this.isAborting()) {
					queueManager.saveTask(this);
					return false;
				}
			}

		} finally {
			if (books != null)
				try {
					books.close();
				} catch (Exception e)
				{
					// Ignore failures, but log them
					Logger.logError(e, "Failed to close GoodReads books cursor");
				}
			try {
				db.close();
			} catch(Exception ignored)
			{}
		}

		// Notify the user: '15 books processed: 3 sent successfully, 5 with no ISBN and 7 with ISBN but not found in goodreads'
		BookCatalogueApp.showNotification(R.id.NOTIFICATION,
				context.getString(R.string.send_books_to_goodreads),
				context.getString(R.string.send_all_to_goodreads_result, mCount, mSent, mNoIsbn, mNotFound),
				BookCatalogueApp.getAppToForegroundIntent(context));

		return true;
	}

	/**
	 * Make a more informative description
	 */
	@Override 
	public String getDescription() {
		return super.getDescription() + " (" + BookCatalogueApp.getResourceString(R.string.x_of_y, mCount, mTotalBooks) + ")";
	}

	@Override
	public long getCategory() {
		return BCQueueManager.CAT_GOODREADS_EXPORT_ALL;
	}
}
