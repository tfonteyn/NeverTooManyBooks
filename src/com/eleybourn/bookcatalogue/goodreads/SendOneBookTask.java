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

package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Task;

/**
 * Task to send a single books details to Goodreads.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 *
 * @author Philip Warner
 */
public class SendOneBookTask
        extends SendBooksTask {

    private static final long serialVersionUID = 8585857100291691934L;

    /** ID of book to send. */
    private final long mBookId;

    /**
     * Constructor. Save book ID.
     *
     * @param bookId Book to send
     */
    SendOneBookTask(final long bookId) {
        super(BookCatalogueApp.getResString(R.string.gr_send_book_to_goodreads, bookId));
        mBookId = bookId;
    }

    /**
     * Perform the main task.
     */
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final Context context,
                           @NonNull final GoodreadsManager grManager) {

        // Use the app context; the calling activity may go away
        try (DBA db = new DBA(context.getApplicationContext());
             BookCursor bookCursor = db.fetchBookForGoodreadsCursor(mBookId)) {
            final BookRowView bookCursorRow = bookCursor.getCursorRow();
            while (bookCursor.moveToNext()) {
                if (!sendOneBook(queueManager, context, grManager, db, bookCursorRow)) {
                    // quit on error
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int getCategory() {
        return Task.CAT_GOODREADS_EXPORT_ONE;
    }
}
