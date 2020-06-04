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

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.RowDataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.events.GrNoIsbnEvent;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.events.GrNoMatchEvent;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * This Task *MUST* be serializable hence can not contain
 * any references to UI components or similar objects.
 */
public abstract class SendBooksLegacyTaskBase
        extends TQTask {

    /** Timeout before declaring network failure. */
    private static final int FIVE_MINUTES = 300;

    private static final long serialVersionUID = -7348431827842548151L;

    /** Number of books with no ISBN. */
    int mNoIsbn;
    /** Number of books that had ISBN but could not be found. */
    int mNotFound;
    /** Number of books successfully sent. */
    int mSent;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    SendBooksLegacyTaskBase(@NonNull final String description) {
        super(description);
    }

    /**
     * Run the task, log exceptions.
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    @Override
    public boolean run(@NonNull final QueueManager queueManager) {
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());
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
     * @param rowData    the book data to send
     *
     * @return {@code false} on failure, {@code true} on success
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean sendOneBook(@NonNull final QueueManager queueManager,
                        @NonNull final Context context,
                        @NonNull final GoodreadsHandler apiHandler,
                        @NonNull final DAO db,
                        @NonNull final RowDataHolder rowData) {

        @GrStatus.Status
        int status;
        try {
            status = apiHandler.sendOneBook(context, db, rowData);

        } catch (@NonNull final CredentialsException e) {
            setLastException(e);
            status = GrStatus.CREDENTIALS_ERROR;

        } catch (@NonNull final Http404Exception e) {
            setLastException(e);
            status = GrStatus.BOOK_NOT_FOUND;

        } catch (@NonNull final IOException e) {
            setLastException(e);
            status = GrStatus.IO_ERROR;

        } catch (@NonNull final RuntimeException e) {
            setLastException(e);
            status = GrStatus.UNEXPECTED_ERROR;
        }

        final long bookId = rowData.getLong(DBDefinitions.KEY_PK_ID);

        // update the current status, so it can be displayed to the user continuously.
        setLastExtStatus(status);

        switch (status) {
            case GrStatus.COMPLETED:
                // Record the change
                db.setGoodreadsSyncDate(bookId);
                mSent++;
                return true;

            case GrStatus.NO_ISBN:
                storeEvent(new GrNoIsbnEvent(context, bookId));
                mNoIsbn++;
                // not a success, but don't try again until the user acts on the stored event
                return true;

            case GrStatus.BOOK_NOT_FOUND:
                storeEvent(new GrNoMatchEvent(context, bookId));
                mNotFound++;
                // not a success, but don't try again until the user acts on the stored event
                return true;

            case GrStatus.IO_ERROR:
                // wait 5 minutes on network errors.
                if (getRetryDelay() > FIVE_MINUTES) {
                    setRetryDelay(FIVE_MINUTES);
                }
                queueManager.updateTask(this);
                return false;

            case GrStatus.AUTHORIZATION_ALREADY_GRANTED:
            case GrStatus.AUTHORIZATION_SUCCESSFUL:
            case GrStatus.AUTHORIZATION_FAILED:
            case GrStatus.AUTHORIZATION_NEEDED:
            case GrStatus.AUTHENTICATION_FAILED:
            case GrStatus.CREDENTIALS_MISSING:
            case GrStatus.CREDENTIALS_ERROR:
            case GrStatus.TASK_QUEUED_WITH_SUCCESS:
            case GrStatus.IMPORT_TASK_ALREADY_QUEUED:
            case GrStatus.EXPORT_TASK_ALREADY_QUEUED:
            case GrStatus.CANCELLED:
            case GrStatus.NO_INTERNET:
            case GrStatus.UNEXPECTED_ERROR:
            case GrStatus.NOT_FOUND:
            default:
                queueManager.updateTask(this);
                return false;
        }
    }
}
