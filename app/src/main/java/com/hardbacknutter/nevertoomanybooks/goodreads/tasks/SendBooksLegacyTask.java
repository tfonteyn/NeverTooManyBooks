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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;

/**
 * Background task class to send all books in the database to Goodreads.
 * <p>
 * This Task *MUST* be serializable hence can not contain
 * any references to UI components or similar objects.
 */
class SendBooksLegacyTask
        extends SendBooksLegacyTaskBase {

    /** Log tag. */
    private static final String TAG = "SendBooksLegacyTask";
    private static final long serialVersionUID = -789131278094482961L;

    /** Flag: send only the updated, or all books. */
    private final boolean mUpdatesOnly;

    /** Last book id processed. */
    private long mLastId;
    /** Total count of books processed. */
    private int mCount;
    /** Total count of books that are in cursor. */
    private int mTotalBooks;

    /**
     * Constructor.
     *
     * @param description for the task
     * @param updatesOnly {@code true} to send updated books only, {@code false} for all books.
     */
    SendBooksLegacyTask(@NonNull final String description,
                        final boolean updatesOnly) {
        super(description);
        mUpdatesOnly = updatesOnly;
    }

    /**
     * Perform the main task. Called from within {@link #run}
     * <p>
     * Deal with restarts by using mLastId as starting point.
     * (Remember: the task gets serialized to the taskqueue database.)
     *
     * @param context    Current context
     * @param apiHandler the Goodreads Manager
     *
     * @return {@code true} on success.
     */
    @Override
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final Context context,
                           @NonNull final GoodreadsHandler apiHandler) {

        try (DAO db = new DAO(TAG);
             Cursor cursor = db.fetchBooksForExportToGoodreads(mLastId, mUpdatesOnly)) {
            final RowDataHolder rowData = new CursorRow(cursor);
            mTotalBooks = cursor.getCount() + mCount;
            boolean needsRetryReset = true;
            while (cursor.moveToNext()) {
                if (!sendOneBook(queueManager, context, apiHandler, db, rowData)) {
                    // quit on error
                    return false;
                }

                // Update internal status
                mCount++;
                mLastId = rowData.getLong(DBDefinitions.KEY_PK_ID);
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

        Notifier.show(context, Notifier.CHANNEL_INFO,
                      context.getString(R.string.gr_menu_send_to_goodreads),
                      context.getString(R.string.gr_info_send_all_books_results,
                                               mCount, mSent, mNoIsbn, mNotFound));
        return true;
    }

    @Override
    public int getCategory() {
        return TQTask.CAT_EXPORT_ALL;
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
