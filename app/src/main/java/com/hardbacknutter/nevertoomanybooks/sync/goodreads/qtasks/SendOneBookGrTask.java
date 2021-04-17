/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks;

import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;

/**
 * Task to send a single books details to Goodreads.
 * This is used during retrying a formerly failed book and is
 * <strong>initiated by the queue manager</strong>.
 */
public class SendOneBookGrTask
        extends SendBooksGrTaskBase {

    private static final long serialVersionUID = 3836442077648262220L;

    /** id of book to send. */
    private final long mBookId;

    /**
     * Constructor.
     *
     * @param description for the task
     * @param bookId      Book to send
     */
    public SendOneBookGrTask(@NonNull final String description,
                             @IntRange(from = 1) final long bookId) {
        super(description);
        mBookId = bookId;
    }

    @Override
    protected TaskStatus send(@NonNull final QueueManager queueManager,
                              @NonNull final GoodreadsManager grManager) {

        final GoodreadsDao grDao = grManager.getGoodreadsDao();
        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();

        try (Cursor cursor = grDao.fetchBookForExport(mBookId)) {
            final DataHolder bookData = new CursorRow(cursor);
            if (cursor.moveToFirst()) {
                return sendOneBook(queueManager, grManager, grDao, bookshelfDao, bookData);
            }
        }
        // No book to send is considered a success
        return TaskStatus.Success;
    }

    @Override
    public int getCategory() {
        return GrBaseTask.CAT_EXPORT_ONE_BOOK;
    }
}
