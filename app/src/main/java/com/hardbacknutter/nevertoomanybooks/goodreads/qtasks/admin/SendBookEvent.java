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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.EditBookActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.SendOneBookGrTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQTask;

/**
 * All Event objects resulting from sending books to Goodreads.
 * <p>
 * Can represent a generic event, but subclassing is likely better.
 */
public class SendBookEvent
        extends TQEvent {

    private static final long serialVersionUID = 3522202309685864404L;
    private final long mBookId;

    /**
     * Constructor.
     *
     * @param description for this event
     * @param bookId      the book this event is for
     */
    protected SendBookEvent(@NonNull final String description,
                            @IntRange(from = 1) final long bookId) {
        super(description);
        mBookId = bookId;
    }

    /**
     * Get the related Book ID.
     *
     * @return id of related book.
     */
    @IntRange(from = 1)
    public long getBookId() {
        return mBookId;
    }

    /**
     * Resubmit this book and delete this event.
     *
     * @param context Current context
     */
    public void retry(@NonNull final Context context) {
        final QueueManager qm = QueueManager.getInstance();

        final String desc = context.getString(R.string.gr_send_book_to_goodreads, getBookId());
        final TQTask task = new SendOneBookGrTask(desc, getBookId());
        qm.enqueueTask(QueueManager.Q_SMALL_JOBS, task);
        qm.deleteEvent(getId());
    }

    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final List<ContextDialogItem> menuItems,
                                    @NonNull final BookDao db) {
        // RETRY EVENT
        final String isbn = db.getBookIsbn(getBookId());
        if (isbn != null && !isbn.isEmpty()) {
            menuItems.add(new ContextDialogItem(context.getString(R.string.action_retry),
                                                () -> retry(context)));
        }

        // EDIT BOOK
        menuItems.add(
                new ContextDialogItem(context.getString(R.string.action_edit_ellipsis), () -> {
                    final Intent intent = new Intent(context, EditBookActivity.class)
                            .putExtra(DBDefinitions.KEY_PK_ID, getBookId());
                    context.startActivity(intent);
                }));

        super.addContextMenuItems(context, menuItems, db);
    }
}
