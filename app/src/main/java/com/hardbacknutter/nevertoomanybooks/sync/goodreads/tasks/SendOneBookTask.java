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
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.network.HttpStatusException;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUnavailableException;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.SendOneBookGrTQTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookGrTQTask}. The core of the task is (should be) identical.
 */
public class SendOneBookTask
        extends GrTaskBase {

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
            throws NetworkUnavailableException,
                   CredentialsException, IOException, HttpStatusException,
                   DiskFullException, ExternalStorageException {

        final GoodreadsAuth grAuth = new GoodreadsAuth();
        if (!checkCredentials(context, grAuth)) {
            return new GrStatus(GrStatus.CREDENTIALS_MISSING);
        }

        if (isCancelled()) {
            return new GrStatus(GrStatus.CANCELLED);
        }

        final GoodreadsManager grManager = new GoodreadsManager(context, grAuth);

        try (Cursor cursor = grManager.getGoodreadsDao().fetchBookForExport(mBookId)) {
            if (cursor.moveToFirst()) {
                if (isCancelled()) {
                    return new GrStatus(GrStatus.CANCELLED);
                }
                publishProgress(1, context.getString(R.string.progress_msg_sending));

                final DataHolder bookData = new CursorRow(cursor);

                final GrStatus.SendBook status = grManager.sendBook(bookData);
                if (status == GrStatus.SendBook.Success) {
                    // Record the update
                    grManager.getGoodreadsDao().setSyncDate(mBookId);
                }
                return status.getGrStatus();

            } else {
                // THIS REALLY SHOULD NOT HAPPEN: we did not find the book
                // in our database which we wanted to send
                if (BuildConfig.DEBUG /* always */) {
                    throw new IllegalStateException("Book not found: bookId=" + mBookId);
                }
                return new GrStatus(GrStatus.FAILED_BOOK_NOT_FOUND_LOCALLY);
            }
        }
    }
}
