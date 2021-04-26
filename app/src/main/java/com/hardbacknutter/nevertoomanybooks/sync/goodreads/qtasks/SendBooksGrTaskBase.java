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

import android.content.Context;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteParsingException;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.admin.SendBookEvent;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

public abstract class SendBooksGrTaskBase
        extends GrBaseTask {

    /** Timeout before declaring network failure. */
    private static final int FIVE_MINUTES = 300;

    private static final long serialVersionUID = -7348431827842548151L;

    /** Number of books without ISBN. */
    private int mNoIsbn;
    /** Number of books that had ISBN but could not be found. */
    private int mNotFound;
    /** Number of books successfully sent. */
    private int mSent;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    SendBooksGrTaskBase(@NonNull final String description) {
        super(description);
    }

    int getNumberOfBooksWithoutIsbn() {
        return mNoIsbn;
    }

    int getNumberOfBooksNotFound() {
        return mNotFound;
    }

    int getNumberOfBooksSent() {
        return mSent;
    }

    @Override
    public TaskStatus doWork(@NonNull final Context context,
                             @NonNull final QueueManager queueManager) {
        try {
            // can we reach the site at all ?
            NetworkUtils.ping(GoodreadsManager.BASE_URL);

            final GoodreadsAuth grAuth = new GoodreadsAuth();
            final GoodreadsManager grManager = new GoodreadsManager(context, grAuth);
            if (grAuth.hasValidCredentials(context)) {
                return send(queueManager, grManager);
            }
        } catch (@NonNull final IOException ignore) {
            // Only wait 5 minutes max on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
        }

        return TaskStatus.Requeue;
    }

    /**
     * Start the send process.
     *
     * @param queueManager the QueueManager
     * @param grManager    the Goodreads Manager
     *
     * @return Status
     */
    protected abstract TaskStatus send(@NonNull QueueManager queueManager,
                                       @NonNull GoodreadsManager grManager);

    /**
     * Try to export one book.
     *
     * @param grManager    the Goodreads Manager
     * @param bookshelfDao Database Access
     * @param bookData     the book data to send
     *
     * @return Status
     */
    TaskStatus sendOneBook(@NonNull final QueueManager queueManager,
                           @NonNull final GoodreadsManager grManager,
                           @NonNull final GoodreadsDao grDao,
                           @NonNull final BookshelfDao bookshelfDao,
                           @NonNull final DataHolder bookData) {

        final long bookId = bookData.getLong(DBKey.PK_ID);

        try {
            @GrStatus.Status
            final int grStatus = grManager.sendOneBook(bookshelfDao, grDao, bookData);
            setLastExtStatus(grStatus);
            if (grStatus == GrStatus.SUCCESS) {
                mSent++;
                grDao.setSyncDate(bookId);
                return TaskStatus.Success;

            } else if (grStatus == GrStatus.FAILED_BOOK_HAS_NO_ISBN) {
                mNoIsbn++;
                storeEvent(new GrNoIsbnEvent(grManager.getContext(), bookId));
                return TaskStatus.Failed;
            }

        } catch (@NonNull final HttpNotFoundException | SiteParsingException e) {
            setLastExtStatus(GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS, e);
            storeEvent(new GrNoMatchEvent(grManager.getContext(), bookId));
            mNotFound++;
            return TaskStatus.Failed;

        } catch (@NonNull final CredentialsException e) {
            setLastExtStatus(GrStatus.FAILED_CREDENTIALS, e);
            // Requeue, so we can retry after the user corrects their credentials

        } catch (@NonNull final DiskFullException e) {
            setLastExtStatus(GrStatus.FAILED_DISK_FULL, e);
            // Requeue, so we can retry after the user makes space

        } catch (@NonNull final IOException e) {
            setLastExtStatus(GrStatus.FAILED_IO_EXCEPTION, e);
            // wait 5 minutes on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
            // Requeue

        } catch (@NonNull final RuntimeException e) {
            // catch all, as we REALLY don't want the whole task to fail.
            setLastExtStatus(GrStatus.FAILED_UNEXPECTED_EXCEPTION, e);
            // Requeue
        }

        queueManager.updateTask(this);
        return TaskStatus.Requeue;
    }

    /**
     * Event indicating the book's ISBN was blank.
     */
    private static class GrNoIsbnEvent
            extends SendBookEvent
            implements TipManager.TipOwner {

        private static final long serialVersionUID = -5466960636472729577L;

        GrNoIsbnEvent(@NonNull final Context context,
                      @IntRange(from = 1) final long bookId) {
            super(context.getString(R.string.warning_no_isbn_stored_for_book), bookId);
        }

        @Override
        @StringRes
        public int getTip() {
            return R.string.gr_info_no_isbn;
        }
    }

    /**
     * Event indicating the book could not be found at Goodreads.
     */
    private static class GrNoMatchEvent
            extends SendBookEvent
            implements TipManager.TipOwner {

        private static final long serialVersionUID = -8047306486727741746L;

        GrNoMatchEvent(@NonNull final Context context,
                       @IntRange(from = 1) final long bookId) {
            super(context.getString(R.string.warning_no_matching_book_found), bookId);
        }

        @Override
        @StringRes
        public int getTip() {
            return R.string.gr_info_no_match;
        }
    }
}
