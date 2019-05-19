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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursorRow;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.Task;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;

/**
 * Background task class to send all books in the database to Goodreads.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 *
 * @author Philip Warner
 */
class GrSendAllBooksTask
        extends GrSendBooksTaskBase {

    private static final long serialVersionUID = -1933000305276643875L;
    /**
     * Flag indicating if it should only send UPDATED books to Goodreads;
     * {@code false} == all books.
     */
    private final boolean mUpdatesOnly;

    /** Last book ID processed. */
    private long mLastId;
    /** Total count of books processed. */
    private int mCount;
    /** Total count of books that are in cursor. */
    private int mTotalBooks;

    /**
     * Constructor.
     */
    GrSendAllBooksTask(@NonNull final String description,
                       final boolean updatesOnly) {
        super(description);
        mUpdatesOnly = updatesOnly;
    }

    /**
     * Do the main of the task. Called from within {@link #run}
     * Deal with restarts by using mLastId as starting point.
     *
     * @return {@code true} on success.
     */
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final Context context,
                           @NonNull final GoodreadsManager grManager) {

        try (DAO db = new DAO();
             BookCursor bookCursor = db.fetchBooksForExportToGoodreads(mLastId, mUpdatesOnly)) {
            final BookCursorRow bookCursorRow = bookCursor.getCursorRow();
            mTotalBooks = bookCursor.getCount() + mCount;
            boolean needsRetryReset = true;
            while (bookCursor.moveToNext()) {
                // Try to export one book
                if (!sendOneBook(queueManager, context, grManager, db, bookCursorRow)) {
                    // quit on error
                    return false;
                }

                // Update internal status
                mCount++;
                mLastId = bookCursor.getId();
                // If we have done one successfully, reset the counter so a
                // subsequent network error does not result in a long delay
                if (needsRetryReset) {
                    needsRetryReset = false;
                    resetRetryCounter();
                }

                queueManager.updateTask(this);

                if (isAborting()) {
                    queueManager.updateTask(this);
                    return false;
                }
            }
        }

        // Notify the user: '15 books processed:
        // 3 sent successfully, 5 with no ISBN and 7 with ISBN but not found in Goodreads'
        App.showNotification(context, R.string.gr_title_send_book,
                             context.getString(R.string.gr_send_all_books_results,
                                               mCount, mSent, mNoIsbn,
                                               mNotFound));

        return true;
    }

    /**
     * Make a more informative description.
     *
     * @param context Current context
     */
    @NonNull
    @Override
    @CallSuper
    public String getDescription(@NonNull final Context context) {
        return super.getDescription(context)
                + " (" + context.getString(R.string.x_of_y, mCount, mTotalBooks) + ')';
    }

    @Override
    public int getCategory() {
        return Task.CAT_GOODREADS_EXPORT_ALL;
    }
}
