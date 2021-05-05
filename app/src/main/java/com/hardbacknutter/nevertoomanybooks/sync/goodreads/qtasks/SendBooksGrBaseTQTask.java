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
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.BookSender;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.admin.SendBookEvent;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;


public abstract class SendBooksGrBaseTQTask
        extends GrBaseTQTask {

    /** Timeout before declaring network failure. */
    private static final int FIVE_MINUTES = 300;

    /**
     * Warning: 2021-05-04: class changed for the post-2.0 update; i.e. new serialVersionUID
     * which means any previously serialized task will be invalid.
     */
    private static final long serialVersionUID = -6222656909387195872L;


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
    SendBooksGrBaseTQTask(@NonNull final String description) {
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
    public TaskStatus doWork(@NonNull final Context context) {
        try {
            // Check for internet every time. This task can be restarted at any time.
            if (!NetworkUtils.isNetworkAvailable()) {
                throw new NetworkUnavailableException(this.getClass().getName());
            }
            // can we reach the site ?
            NetworkUtils.ping(GoodreadsManager.BASE_URL);

            final GoodreadsAuth grAuth = new GoodreadsAuth();
            if (grAuth.getCredentialStatus(context) == GoodreadsAuth.CredentialStatus.Valid) {

                final GoodreadsManager grManager = new GoodreadsManager(context, grAuth);
                final BookSender bookSender = new BookSender(context, grManager);
                return send(context, bookSender);
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
     * @param context Current context
     *
     * @return Status
     */
    protected abstract TaskStatus send(@NonNull final Context context,
                                       @NonNull final BookSender bookSender);

    /**
     * Try to export one book.
     *
     * @param context  Current context
     * @param bookData the book data to send
     *
     * @return Status
     */
    TaskStatus sendOneBook(@NonNull final Context context,
                           @NonNull final BookSender bookSender,
                           @NonNull final DataHolder bookData) {
        try {
            final BookSender.Status status = bookSender.send(bookData);
            setLastExtStatus(status);
            if (status == BookSender.Status.Success) {
                mSent++;
                return TaskStatus.Success;

            } else if (status == BookSender.Status.NoIsbn) {
                mNoIsbn++;
                final long bookId = bookData.getLong(DBKey.PK_ID);
                storeEvent(new GrNoIsbnEvent(context, bookId));
                return TaskStatus.Failed;

            } else if (status == BookSender.Status.NotFound) {
                mNotFound++;
                final long bookId = bookData.getLong(DBKey.PK_ID);
                storeEvent(new GrNoMatchEvent(context, bookId));
                return TaskStatus.Failed;
            }
        } catch (@NonNull final DiskFullException e) {
            setLastExtStatus(GrStatus.FAILED_DISK_FULL_EXCEPTION, e);
            // Requeue, so we can retry after the user corrects this

        } catch (@NonNull final CoverStorageException e) {
            setLastExtStatus(GrStatus.FAILED_STORAGE_EXCEPTION, e);
            // Requeue, so we can retry after the user corrects this

        } catch (@NonNull final IOException e) {
            setLastExtStatus(GrStatus.FAILED_IO_EXCEPTION, e);
            // wait 5 minutes on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
            // Requeue

        } catch (@NonNull final CredentialsException e) {
            setLastExtStatus(GrStatus.CREDENTIALS_INVALID, e);
            // Requeue, so we can retry after the user corrects this

        } catch (@NonNull final RuntimeException e) {
            // catch all, as we REALLY don't want the whole task to fail.
            setLastExtStatus(GrStatus.FAILED_UNEXPECTED_EXCEPTION, e);
            // Requeue
        }

        QueueManager.getInstance().updateTask(this);
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
