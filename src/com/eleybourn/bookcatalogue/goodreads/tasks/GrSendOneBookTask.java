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

package com.eleybourn.bookcatalogue.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Task;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;

/**
 * Task to send a single books details to Goodreads.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 *
 * @author Philip Warner
 */
class GrSendOneBookTask
        extends GrSendBooksTaskBase {

    private static final long serialVersionUID = 8585857100291691934L;

    /** ID of book to send. */
    private final long mBookId;

    /**
     * Constructor. Save book ID.
     *
     * @param bookId Book to send
     */
    GrSendOneBookTask(@NonNull final String description,
                      final long bookId) {
        super(description);
        mBookId = bookId;
    }

    /**
     * Perform the main task.
     *
     * @return {@code true} for success
     */
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final Context context,
                           @NonNull final GoodreadsManager grManager) {

        try (DAO db = new DAO();
             BookCursor bookCursor = db.fetchBookForExportToGoodreads(mBookId)) {
            final BookCursorRow bookCursorRow = bookCursor.getCursorRow();
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
