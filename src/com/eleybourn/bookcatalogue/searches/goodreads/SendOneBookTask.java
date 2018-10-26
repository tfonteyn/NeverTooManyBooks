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
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.goodreads.BookEvents.GrNoIsbnEvent;
import com.eleybourn.bookcatalogue.searches.goodreads.BookEvents.GrNoMatchEvent;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.ExportDisposition;
import com.eleybourn.bookcatalogue.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Task to send a single books details to goodreads.
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
    public boolean run(@NonNull final QueueManager manager, @NonNull final Context context) {
        boolean result = false;
        try {
            result = sendBook(manager, context);
        } catch (NotAuthorizedException e) {
            Logger.error(e, "Not Authorized to send books to GoodReads");
        }
        return result;
    }

    /**
     * Perform the main task
     */
    private boolean sendBook(@NonNull final QueueManager queueManager,
                             @NonNull final Context context) throws NotAuthorizedException {

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
        // get the app context; the underlying activity may go away. Also get DB
        Context ctx = context.getApplicationContext();
        CatalogueDBAdapter db = new CatalogueDBAdapter(ctx);
        db.open();

        try (BooksCursor books = db.fetchBookForGoodreadsCursor(mBookId)) {
            final BookRowView bookRowView = books.getRowView();
            while (books.moveToNext()) {
                // Try to export one book
                ExportDisposition disposition;
                Exception exportException = null;
                try {
                    disposition = grManager.sendOneBook(db, bookRowView);
                } catch (Exception e) {
                    disposition = ExportDisposition.error;
                    exportException = e;
                }

                // Handle the result
                switch (disposition) {
                    case error:
                        this.setException(exportException);
                        queueManager.saveTask(this);
                        return false;
                    case sent:
                        // Record the change
                        db.setGoodreadsSyncDate(books.getId());
                        break;
                    case noIsbn:
                        storeEvent(new GrNoIsbnEvent(books.getId()));
                        break;
                    case notFound:
                        storeEvent(new GrNoMatchEvent(books.getId()));
                        break;
                    case networkError:
                        // Only wait 5 minutes on network errors.
                        if (getRetryDelay() > 300) {
                            setRetryDelay(300);
                        }
                        queueManager.saveTask(this);
                        return false;
                }
            }
        } finally {
            db.close();
        }
        return true;
    }

    @Override
    public int getCategory() {
        return BCQueueManager.CAT_GOODREADS_EXPORT_ONE;
    }

}
