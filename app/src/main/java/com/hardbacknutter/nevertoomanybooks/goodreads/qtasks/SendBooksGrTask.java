/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks;

import android.app.PendingIntent;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;

/**
 * Background task class to send all books in the database to Goodreads.
 */
public class SendBooksGrTask
        extends SendBooksGrTaskBase {

    /** Log tag. */
    private static final String TAG = "GR.SendBooksGrTask";
    private static final long serialVersionUID = -3922988908020406047L;

    /** Flag: send only starting from the last book we did earlier, or all books. */
    private final boolean mFromLastBookId;
    /** Flag: send only the updated, or all books. */
    private final boolean mUpdatesOnly;

    /** Total count of books processed. */
    private int mCount;
    /** Total count of books that are in cursor. */
    private int mTotalBooks;

    /**
     * Constructor.
     *
     * @param description    for the task
     * @param fromLastBookId {@code true} to send from the last book we did earlier,
     *                       {@code false} for all books.
     * @param updatesOnly    {@code true} to send updated books only,
     *                       {@code false} for all books.
     */
    public SendBooksGrTask(@NonNull final String description,
                           final boolean fromLastBookId,
                           final boolean updatesOnly) {
        super(description);
        mFromLastBookId = fromLastBookId;
        mUpdatesOnly = updatesOnly;
    }

    /**
     * Perform the main task. Called from within {@link BaseTQTask#run}
     * <p>
     * Deals with restarts by using mLastId as starting point.
     * (Remember: the task gets serialized to the taskqueue database.)
     * <p>
     * {@inheritDoc}
     */
    @Override
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final GoodreadsManager grManager) {

        long lastBookSend;
        if (mFromLastBookId) {
            lastBookSend = grManager.getLastBookIdSend();
        } else {
            lastBookSend = 0;
        }

        try (final DAO db = new DAO(TAG);
             final Cursor cursor = db.fetchBooksForGoodreadsExport(lastBookSend, mUpdatesOnly)) {
            final DataHolder bookData = new CursorRow(cursor);
            mTotalBooks = cursor.getCount() + mCount;

            boolean needsRetryReset = true;
            while (cursor.moveToNext()) {
                if (!sendOneBook(queueManager, grManager, db, bookData)) {
                    // quit on error
                    return false;
                }

                // Update internal status
                mCount++;
                lastBookSend = bookData.getLong(DBDefinitions.KEY_PK_ID);
                // If we have done one successfully, reset the counter so a
                // subsequent network error does not result in a long delay
                if (needsRetryReset) {
                    needsRetryReset = false;
                    resetRetryCounter();
                }

                queueManager.updateTask(this);

                if (isCancelled()) {
                    queueManager.updateTask(this);
                    return false;
                }
            }
        }

        // store the last book id we updated; used to reduce future (needless) checks.
        grManager.putLastBookIdSend(lastBookSend);

        final PendingIntent pendingIntent = Notifier
                .createPendingIntent(grManager.getAppContext(), BooksOnBookshelf.class);
        Notifier.getInstance(grManager.getAppContext())
                .sendInfo(grManager.getAppContext(), Notifier.ID_GOODREADS, pendingIntent,
                          R.string.gr_send_to_goodreads,
                          grManager.getAppContext()
                                   .getString(R.string.gr_info_send_all_books_results,
                                              mCount,
                                              getNumberOfBooksSent(),
                                              getNumberOfBooksWithoutIsbn(),
                                              getNumberOfBooksNotFound()));
        return true;
    }

    @Override
    public int getCategory() {
        return TQTask.CAT_EXPORT;
    }

    /**
     * Provide a more informative description.
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
}
