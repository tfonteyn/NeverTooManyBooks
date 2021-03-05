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
package com.hardbacknutter.nevertoomanybooks.goodreads.tasks;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.SendOneBookGrTask;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.HttpNotFoundException;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookGrTask}. The core of the task is (should be) identical.
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
    public void start(@IntRange(from = 1) final long bookId) {
        mBookId = bookId;
        execute(R.id.TASK_ID_GR_SEND_ONE_BOOK);
    }

    @NonNull
    @Override
    @WorkerThread
    protected GrStatus doWork(@NonNull final Context context) {
        Thread.currentThread().setName(TAG + mBookId);

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

            final GoodreadsManager grManager = new GoodreadsManager(context, grAuth);
            final GoodreadsDao grDao = grManager.getGoodreadsDao();

            try (BookDao bookDao = new BookDao(TAG);
                 Cursor cursor = grDao.fetchBookForExport(mBookId)) {

                if (cursor.moveToFirst()) {
                    if (isCancelled()) {
                        return new GrStatus(GrStatus.CANCELLED);
                    }
                    publishProgressStep(0, context.getString(R.string.progress_msg_sending));


                    final DataHolder bookData = new CursorRow(cursor);
                    @GrStatus.Status
                    final int status = grManager.sendOneBook(bookDao, grDao, bookData);
                    if (status == GrStatus.SUCCESS) {
                        // Record the update
                        grDao.setSyncDate(mBookId);
                    }
                    return new GrStatus(status);

                } else {
                    // THIS REALLY SHOULD NOT HAPPEN: we did not find the book
                    // in our database which we wanted to send
                    if (BuildConfig.DEBUG /* always */) {
                        throw new IllegalStateException("Book not found: bookId=" + mBookId);
                    }
                    return new GrStatus(GrStatus.FAILED_BOOK_NOT_FOUND_LOCALLY);
                }
            }

        } catch (@NonNull final CredentialsException e) {
            return new GrStatus(GrStatus.FAILED_CREDENTIALS);

        } catch (@NonNull final HttpNotFoundException | GeneralParsingException e) {
            return new GrStatus(GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS);

        } catch (@NonNull final IOException e) {
            Logger.error(context, TAG, e);
            return new GrStatus(GrStatus.FAILED_IO_EXCEPTION, e);
        }
    }
}
