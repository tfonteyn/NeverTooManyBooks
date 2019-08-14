/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.cursors.BookCursor;
import com.hardbacknutter.nevertoomanybooks.database.cursors.MappedCursorRow;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.Task;
import com.hardbacknutter.nevertoomanybooks.searches.goodreads.GoodreadsManager;

/**
 * Task to send a single books details to Goodreads.
 * <p>
 * A Task *MUST* be serializable.
 * This means that it can not contain any references to UI components or similar objects.
 */
class SendOneBookLegacyTask
        extends SendBooksLegacyTaskBase {

    private static final long serialVersionUID = 8585857100291691934L;

    /** ID of book to send. */
    private final long mBookId;

    /**
     * Constructor.
     *
     * @param description for the task
     * @param bookId      Book to send
     */
    SendOneBookLegacyTask(@NonNull final String description,
                          final long bookId) {
        super(description);
        mBookId = bookId;
    }

    /**
     * Perform the main task. Called from within {@link #run}
     *
     * @param queueManager QueueManager
     * @param context      Current context
     * @param grManager    the Goodreads Manager
     *
     * @return {@code true} for success
     */
    protected boolean send(@NonNull final QueueManager queueManager,
                           @NonNull final Context context,
                           @NonNull final GoodreadsManager grManager) {

        try (DAO db = new DAO();
             BookCursor bookCursor = db.fetchBookForExportToGoodreads(mBookId)) {
            final MappedCursorRow cursorRow = bookCursor.getCursorRow();
            while (bookCursor.moveToNext()) {
                if (!sendOneBook(queueManager, context, grManager, db, cursorRow)) {
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
