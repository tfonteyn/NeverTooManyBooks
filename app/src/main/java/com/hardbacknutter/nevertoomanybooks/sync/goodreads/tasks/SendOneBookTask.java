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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.dao.GoodreadsDao;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.network.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.network.HttpNotFoundException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.searchengines.SiteParsingException;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.SendOneBookGrTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookGrTask}. The core of the task is (should be) identical.
 */
public class SendOneBookTask
        extends LTask<GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.SendOneBookTask";

    /** The book to send. */
    private long mBookId;

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    public SendOneBookTask(@NonNull final TaskListener<GrStatus> taskListener) {
        super(R.id.TASK_ID_GR_SEND_ONE_BOOK, TAG, taskListener);
    }


    /**
     * Start sending.
     *
     * @param bookId the book to send
     */
    public void send(@IntRange(from = 1) final long bookId) {
        mBookId = bookId;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected GrStatus doWork(@NonNull final Context context)
            throws DiskFullException {

        try {
            if (!NetworkUtils.isNetworkAvailable()) {
                return new GrStatus(GrStatus.FAILED_NETWORK_UNAVAILABLE);
            }

            final GoodreadsAuth grAuth = new GoodreadsAuth();
            if (!grAuth.hasValidCredentials(context)) {
                return new GrStatus(GrStatus.FAILED_CREDENTIALS);
            }

            if (isCancelled()) {
                return new GrStatus(GrStatus.CANCELLED);
            }

            final GoodreadsManager grManager = new GoodreadsManager(context, grAuth);
            final GoodreadsDao grDao = grManager.getGoodreadsDao();

            try (Cursor cursor = grDao.fetchBookForExport(mBookId)) {
                if (cursor.moveToFirst()) {
                    if (isCancelled()) {
                        return new GrStatus(GrStatus.CANCELLED);
                    }
                    publishProgress(1, context.getString(R.string.progress_msg_sending));

                    final DataHolder bookData = new CursorRow(cursor);
                    @GrStatus.Status
                    final int status = grManager.sendOneBook(
                            ServiceLocator.getInstance().getBookshelfDao(), grDao, bookData);

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

        } catch (@NonNull final HttpNotFoundException | SiteParsingException e) {
            return new GrStatus(GrStatus.FAILED_BOOK_NOT_FOUND_ON_GOODREADS);

        } catch (@NonNull final IOException e) {
            Logger.error(TAG, e);
            return new GrStatus(GrStatus.FAILED_IO_EXCEPTION, e);
        }
    }
}
