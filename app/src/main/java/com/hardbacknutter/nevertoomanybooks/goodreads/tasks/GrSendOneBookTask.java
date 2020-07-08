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

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.api.Http404Exception;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookGrTask} which is used internally by
 * {@link SendBooksGrTask}. The core of the task is (should be) identical.
 */
public class GrSendOneBookTask
        extends VMTask<GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.SendOneBook";

    /** The book to send. */
    private long mBookId;

    /**
     * Constructor.
     *
     * @param bookId the book to send
     */
    public void startTask(final long bookId) {
        mBookId = bookId;
        execute(R.id.TASK_ID_GR_SEND_ONE_BOOK);
    }

    @Override
    @NonNull
    @WorkerThread
    protected GrStatus doWork() {
        Thread.currentThread().setName(TAG + mBookId);
        final Context context = LocaleUtils.applyLocale(App.getTaskContext());

        try {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return new GrStatus(GrStatus.FAILED_NETWORK_UNAVAILABLE);
            }

            final GoodreadsAuth grAuth = new GoodreadsAuth(context);
            if (!grAuth.hasValidCredentials(context)) {
                return new GrStatus(GrStatus.FAILED_CREDENTIALS);
            }

            if (isCancelled()) {
                return new GrStatus(GrStatus.CANCELLED);
            }

            try (DAO db = new DAO(TAG);
                 Cursor cursor = db.fetchBookForGoodreadsExport(mBookId)) {
                if (cursor.moveToFirst()) {
                    if (isCancelled()) {
                        return new GrStatus(GrStatus.CANCELLED);
                    }
                    onProgressStep(0, context.getString(R.string.progress_msg_sending));

                    final GoodreadsHandler apiHandler = new GoodreadsHandler(grAuth);
                    final DataHolder bookData = new CursorRow(cursor);
                    @GrStatus.Status
                    final int status = apiHandler.sendOneBook(context, db, bookData);
                    if (status == GrStatus.SUCCESS) {
                        // Record the update
                        db.setGoodreadsSyncDate(mBookId);
                    }
                    return new GrStatus(status);

                } else {
                    // THIS REALLY SHOULD NOT HAPPEN: we did not find the book
                    // in our database which we wanted to send
                    if (BuildConfig.DEBUG /* always */) {
                        throw new IllegalStateException("Book not found: bookId=" + mBookId);
                    }
                    // pretend it's Goodreads fault.
                    return new GrStatus(GrStatus.FAILED_BOOK_NOT_FOUND_LOCALLY);
                }
            }

        } catch (@NonNull final CredentialsException e) {
            return new GrStatus(GrStatus.FAILED_CREDENTIALS);

        } catch (@NonNull final Http404Exception e) {
            return new GrStatus(GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS);

        } catch (@NonNull final IOException e) {
            Logger.error(context, TAG, e);
            return new GrStatus(GrStatus.FAILED_IO_EXCEPTION, e);
        }
    }
}
