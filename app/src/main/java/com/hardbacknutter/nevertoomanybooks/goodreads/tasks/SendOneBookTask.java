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
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.NotFoundException;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHelper;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;

/**
 * Start a background task that exports a single books to Goodreads.
 * This is used for sending single books, <strong>initiated by the user</strong>.
 * <p>
 * See also {@link SendOneBookLegacyTask} which is used internally by
 * {@link SendBooksLegacyTask}. The core of the task is (should be) identical.
 */
public class SendOneBookTask
        extends TaskBase<Void, GrStatus> {

    /** Log tag. */
    private static final String TAG = "SendOneBookTask";

    /** The book to send. */
    private final long mBookId;

    /**
     * Constructor.
     *
     * @param bookId       the book to send
     * @param taskListener for sending progress and finish messages to.
     */
    public SendOneBookTask(final long bookId,
                           @NonNull final TaskListener<GrStatus> taskListener) {
        super(R.id.TASK_ID_GR_SEND_ONE_BOOK, taskListener);
        mBookId = bookId;
    }

    @Override
    @NonNull
    @WorkerThread
    protected GrStatus doInBackground(final Void... params) {
        Thread.currentThread().setName("GR.SendOneBookTask " + mBookId);
        Context context = App.getLocalizedAppContext();

        GrStatus result;
        try {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                return GrStatus.NoInternet;
            }
            GoodreadsAuth grAuth = new GoodreadsAuth(new SettingsHelper(context));
            GoodreadsHandler apiHandler = new GoodreadsHandler(grAuth);
            if (!grAuth.hasValidCredentials(context)) {
                return GrStatus.CredentialsMissing;
            }

            try (DAO db = new DAO(TAG);
                 Cursor cursor = db.fetchBookForExportToGoodreads(mBookId)) {
                if (cursor.moveToFirst()) {
                    if (isCancelled()) {
                        return GrStatus.Cancelled;
                    }
                    publishProgress(new TaskListener.ProgressMessage(
                            getTaskId(), context.getString(R.string.progress_msg_sending)));

                    final CursorRow cursorRow = new CursorRow(cursor);
                    result = apiHandler.sendOneBook(context, db, cursorRow);
                    if (result == GrStatus.BookSent) {
                        // Record the update
                        db.setGoodreadsSyncDate(mBookId);
                    }
                    return result;
                }
            }
        } catch (@NonNull final CredentialsException e) {
            mException = e;
            Logger.error(context, TAG, e);
            return GrStatus.CredentialsError;
        } catch (@NonNull final NotFoundException e) {
            mException = e;
            Logger.error(context, TAG, e, e.getUrl());
            return GrStatus.NotFound;
        } catch (@NonNull final IOException e) {
            mException = e;
            Logger.error(context, TAG, e);
            return GrStatus.IOError;
        } catch (@NonNull final RuntimeException e) {
            mException = e;
            Logger.error(context, TAG, e);
            return GrStatus.UnexpectedError;
        }

        return GrStatus.UnexpectedError;
    }
}
