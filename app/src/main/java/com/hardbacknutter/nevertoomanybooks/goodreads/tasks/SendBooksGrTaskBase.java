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

import androidx.annotation.NonNull;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.goodreads.events.GrNoIsbnEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.events.GrNoMatchEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

public abstract class SendBooksGrTaskBase
        extends BaseTQTask {

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

    public int getNumberOfBooksWithoutIsbn() {
        return mNoIsbn;
    }

    public int getNumberOfBooksNotFound() {
        return mNotFound;
    }

    public int getNumberOfBooksSent() {
        return mSent;
    }

    /**
     * Run the task.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager,
                       @NonNull final Context context) {
        try {
            // can we reach the site at all ?
            NetworkUtils.ping(context, GoodreadsHandler.BASE_URL);

            final GoodreadsAuth grAuth = new GoodreadsAuth(context);
            final GoodreadsHandler apiHandler = new GoodreadsHandler(grAuth);
            if (grAuth.hasValidCredentials(context)) {
                return send(queueManager, context, apiHandler);
            }
        } catch (@NonNull final IOException ignore) {
            // Only wait 5 minutes max on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }
        }

        return false;
    }

    /**
     * Start the send process.
     *
     * @param context    Current context
     * @param apiHandler the Goodreads Manager
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    protected abstract boolean send(@NonNull QueueManager queueManager,
                                    @NonNull Context context,
                                    @NonNull GoodreadsHandler apiHandler);

    /**
     * Try to export one book.
     *
     * @param context    Current context
     * @param apiHandler the Goodreads Manager
     * @param db         Database Access
     * @param bookData   the book data to send
     *
     * @return {@code false} on failure, {@code true} on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final Context context,
                        @NonNull final GoodreadsHandler apiHandler,
                        @NonNull final DAO db,
                        @NonNull final DataHolder bookData) {

        final long bookId = bookData.getLong(DBDefinitions.KEY_PK_ID);

        try {
            @GrStatus.Status
            int status = apiHandler.sendOneBook(context, db, bookData);
            setLastExtStatus(status);
            if (status == GrStatus.SUCCESS) {
                mSent++;
                db.setGoodreadsSyncDate(bookId);
                return true;

            } else if (status == GrStatus.FAILED_BOOK_HAS_NO_ISBN) {
                // not a success, but don't try again until the user acts on the stored event
                mNoIsbn++;
                storeEvent(new GrNoIsbnEvent(context, bookId));
                return true;
            }
            // any other status is a non fatal error

        } catch (@NonNull final CredentialsException e) {
            setLastExtStatus(GrStatus.FAILED_CREDENTIALS, e);

        } catch (@NonNull final Http404Exception e) {
            setLastExtStatus(GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS, e);
            storeEvent(new GrNoMatchEvent(context, bookId));
            mNotFound++;
            // not a success, but don't try again until the user acts on the stored event
            return true;

        } catch (@NonNull final IOException e) {
            setLastExtStatus(GrStatus.FAILED_IO_EXCEPTION, e);
            // wait 5 minutes on network errors.
            if (getRetryDelay() > FIVE_MINUTES) {
                setRetryDelay(FIVE_MINUTES);
            }

        } catch (@NonNull final RuntimeException e) {
            // catch all, as we REALLY don't want the whole task to fail.
            setLastExtStatus(GrStatus.FAILED_UNEXPECTED_EXCEPTION, e);
        }

        queueManager.updateTask(this);
        return false;
    }
}
