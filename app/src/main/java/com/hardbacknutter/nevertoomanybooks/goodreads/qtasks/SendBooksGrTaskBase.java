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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin.SendBookEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
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

    int getNumberOfBooksWithoutIsbn() {
        return mNoIsbn;
    }

    int getNumberOfBooksNotFound() {
        return mNotFound;
    }

    int getNumberOfBooksSent() {
        return mSent;
    }

    /**
     * Run the task.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager) {
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());
        try {
            // can we reach the site at all ?
            NetworkUtils.ping(context, GoodreadsManager.BASE_URL);

            final GoodreadsAuth grAuth = new GoodreadsAuth(context);
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

        return false;
    }

    /**
     * Start the send process.
     *
     * @param queueManager the QueueManager
     * @param grManager    the Goodreads Manager
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    protected abstract boolean send(@NonNull QueueManager queueManager,
                                    @NonNull GoodreadsManager grManager);

    /**
     * Try to export one book.
     *
     * @param grManager the Goodreads Manager
     * @param db         Database Access
     * @param bookData   the book data to send
     *
     * @return {@code false} on failure, {@code true} on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final GoodreadsManager grManager,
                        @NonNull final DAO db,
                        @NonNull final DataHolder bookData) {

        final long bookId = bookData.getLong(DBDefinitions.KEY_PK_ID);

        try {
            @GrStatus.Status
            final int status = grManager.sendOneBook(db, bookData);
            setLastExtStatus(status);
            if (status == GrStatus.SUCCESS) {
                mSent++;
                db.setGoodreadsSyncDate(bookId);
                return true;

            } else if (status == GrStatus.FAILED_BOOK_HAS_NO_ISBN) {
                // not a success, but don't try again until the user acts on the stored event
                mNoIsbn++;
                storeEvent(new GrNoIsbnEvent(grManager.getAppContext(), bookId));
                return true;
            }
            // any other status is a non fatal error

        } catch (@NonNull final CredentialsException e) {
            setLastExtStatus(GrStatus.FAILED_CREDENTIALS, e);

        } catch (@NonNull final Http404Exception e) {
            setLastExtStatus(GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS, e);
            storeEvent(new GrNoMatchEvent(grManager.getAppContext(), bookId));
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

    /**
     * Event indicating the book's ISBN was blank.
     */
    private static class GrNoIsbnEvent
            extends SendBookEvent
            implements TipManager.TipOwner {

        private static final long serialVersionUID = -5466960636472729577L;

        GrNoIsbnEvent(@NonNull final Context context,
                      final long bookId) {
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
                       final long bookId) {
            super(context.getString(R.string.warning_no_matching_book_found), bookId);
        }

        @Override
        @StringRes
        public int getTip() {
            return R.string.gr_info_no_match;
        }
    }
}
