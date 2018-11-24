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
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.BookEvents.GrNoIsbnEvent;
import com.eleybourn.bookcatalogue.searches.goodreads.BookEvents.GrNoMatchEvent;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.ExportDisposition;
import com.eleybourn.bookcatalogue.taskqueue.GenericTask;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Task to send a single books details to Goodreads.
 *
 * @author Philip Warner
 */
public class SendOneBookTask extends GenericTask {
    private static final long serialVersionUID = 8585857100291691934L;

    /** ID of book to send */
    private final long mBookId;

    /**
     * Constructor. Save book ID.
     *
     * @param bookId Book to send
     */
    public SendOneBookTask(final long bookId) {
        super(BookCatalogueApp.getResourceString(R.string.gr_send_book_to_goodreads, bookId));
        mBookId = bookId;
    }

    /**
     * Run the task, log exceptions.
     */
    @Override
    public boolean run(final @NonNull QueueManager manager, final @NonNull Context context) {
        boolean result = false;
        try {
            result = sendBook(manager, context);
        } catch (NotAuthorizedException e) {
            Logger.error(e, "Not Authorized to send books to Goodreads");
        }
        return result;
    }

    /**
     * Perform the main task
     */
    private boolean sendBook(final @NonNull QueueManager queueManager,
                             final @NonNull Context context) throws NotAuthorizedException {

        // ENHANCE: Work out a way of checking if GR site is up
        //if (!Utils.hostIsAvailable(context, "www.goodreads.com"))
        //	return false;

        if (!Utils.isNetworkAvailable(context)) {
            // Only wait 5 minutes on network errors.
            if (getRetryDelay() > 300) {
                setRetryDelay(300);
            }
            return false;
        }

        GoodreadsManager grManager = new GoodreadsManager();
        // Ensure we are allowed
        if (!grManager.hasValidCredentials()) {
            throw new NotAuthorizedException();
        }
        // Use the app context; the calling activity may go away
        try (CatalogueDBAdapter db = new CatalogueDBAdapter(context.getApplicationContext());
             BookCursor books = db.fetchBookForGoodreadsCursor(mBookId)) {
            final BookRowView bookCursorRow = books.getCursorRow();
            while (books.moveToNext()) {
                // Try to export one book
                ExportDisposition disposition;
                Exception exportException = null;
                try {
                    disposition = grManager.sendOneBook(db, bookCursorRow);
                } catch (Exception e) {
                    disposition = ExportDisposition.error;
                    exportException = e;
                }

                // Handle the result
                switch (disposition) {
                    case error:
                        this.setException(exportException);
                        queueManager.updateTask(this);
                        return false;
                    case sent:
                        // Record the change
                        db.setGoodreadsSyncDate(books.getId());
                        break;
                    case noIsbn:
                        storeEvent(new GrNoIsbnEvent(context, books.getId()));
                        break;
                    case notFound:
                        storeEvent(new GrNoMatchEvent(context, books.getId()));
                        break;
                    case networkError:
                        // Only wait 5 minutes on network errors.
                        if (getRetryDelay() > 300) {
                            setRetryDelay(300);
                        }
                        queueManager.updateTask(this);
                        return false;
                }
            }
        }
        return true;
    }

    @Override
    public int getCategory() {
        return BCQueueManager.CAT_GOODREADS_EXPORT_ONE;
    }

}
