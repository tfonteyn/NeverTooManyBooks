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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;

import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_EVENT_COUNT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_EXCEPTION;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_FAILURE_REASON;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_QUEUED_DATE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_RETRY_DATE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_STATUS_CODE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueDBHelper.KEY_TASK;

/**
 * Cursor subclass used to make accessing Tasks a little easier.
 */
public final class TasksCursor
        extends SQLiteCursor
        implements BindableItemCursor<Task>, Closeable {

    /** Column number of id column. */
    private static int sIdCol = -2;
    /** Column number of date column. */
    private static int sQueuedDateCol = -2;
    /** Column number of retry date column. */
    private static int sRetryDateCol = -2;
    /** Column number of retry count column. */
    private static int sStatusCodeCol = -2;
    /** Column number of Exception column. */
    private static int sTaskCol = -2;
    /** Column number of NoteCount column. */
    private static int sNoteCountCol = -2;
    /** Column number of reason column. */
    private static int sReasonCol = -2;
    /** Column number of Exception column. */
    private static int sExceptionCol = -2;

    /**
     * Constructor, based on SQLiteCursor constructor.
     */
    TasksCursor(@NonNull final SQLiteCursorDriver driver,
                @NonNull final String editTable,
                @NonNull final SQLiteQuery query) {
        super(driver, editTable, query);
    }

    @NonNull
    Date getQueuedDate() {
        if (sQueuedDateCol < 0) {
            sQueuedDateCol = getColumnIndex(KEY_QUEUED_DATE);
        }

        Date date = DateUtils.parseSqlDateTime(getString(sQueuedDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    @NonNull
    Date getRetryDate() {
        if (sRetryDateCol < 0) {
            sRetryDateCol = getColumnIndex(KEY_RETRY_DATE);
        }

        Date date = DateUtils.parseSqlDateTime(getString(sRetryDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    String getStatusCode() {
        if (sStatusCodeCol < 0) {
            sStatusCodeCol = getColumnIndex(KEY_STATUS_CODE);
        }
        return getString(sStatusCodeCol);
    }

    /**
     * Accessor for reason field.
     *
     * @return reason
     */
    public String getReason() {
        if (sReasonCol < 0) {
            sReasonCol = getColumnIndex(KEY_FAILURE_REASON);
        }
        return getString(sReasonCol);
    }

    /**
     * Accessor for Exception field.
     *
     * @return Exception object
     */
    @NonNull
    public Exception getException() {
        if (sExceptionCol < 0) {
            sExceptionCol = getColumnIndex(KEY_EXCEPTION);
        }
        try {
            return (Exception) SerializationUtils.deserializeObject(getBlob(sExceptionCol));
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            // Better then nothing
            return de;
        }
    }

    int getNoteCount() {
        if (sNoteCountCol < 0) {
            sNoteCountCol = getColumnIndex(KEY_EVENT_COUNT);
        }
        return getInt(sNoteCountCol);
    }

    @NonNull
    @Override
    public Task getBindableItem(@NonNull final Context context) {
        if (sTaskCol < 0) {
            sTaskCol = getColumnIndex(KEY_TASK);
        }
        Task task;
        byte[] blob = getBlob(sTaskCol);
        try {
            task = SerializationUtils.deserializeObject(blob);
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            task = new LegacyTask(context);
        }
        task.setId(getId());
        return task;
    }

    @Override
    public long getId() {
        if (sIdCol < 0) {
            sIdCol = getColumnIndex(KEY_PK_ID);
        }
        return getLong(sIdCol);
    }
}
