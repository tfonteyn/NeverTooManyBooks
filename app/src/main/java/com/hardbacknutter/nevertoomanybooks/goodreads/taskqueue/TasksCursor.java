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

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SerializationUtils;

import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_CATEGORY;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_EVENT_COUNT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_EXCEPTION;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_FAILURE_REASON;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_QUEUED_DATE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_RETRY_DATE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_STATUS_CODE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_TASK;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.CKEY_TASK_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.TBL_EVENT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.TBL_TASK;

/**
 * Cursor subclass used to make accessing Tasks a little easier.
 */
public final class TasksCursor
        extends SQLiteCursor
        implements BindableItemCursor, Closeable {

    /** Static Factory object to create the custom cursor. */
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, masterQuery, editTable, query) -> new TasksCursor(masterQuery, editTable, query);

    private static final String ALL_TASKS_QUERY =
            "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e"
            + " WHERE e." + CKEY_TASK_ID + "=t." + CKEY_PK_ID
            + ") AS " + CKEY_EVENT_COUNT
            + " FROM " + TBL_TASK + " t WHERE 1=1 %1$s"
            + " ORDER BY " + CKEY_PK_ID + " DESC";

    private static final String ACTIVE_TASKS_QUERY =
            "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e"
            + " WHERE e." + CKEY_TASK_ID + "=t." + CKEY_PK_ID
            + ") AS " + CKEY_EVENT_COUNT
            + " FROM " + TBL_TASK + " t "
            + " WHERE NOT " + CKEY_STATUS_CODE
            + " IN ('S','F') %1$s"
            + " ORDER BY " + CKEY_PK_ID + " DESC";

    /** Column number of id column. */
    private static int sIdCol = -1;
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
    private TasksCursor(@NonNull final SQLiteCursorDriver driver,
                        @NonNull final String editTable,
                        @NonNull final SQLiteQuery query) {
        super(driver, editTable, query);
    }

    /**
     * Static method to get a TaskExceptions Cursor.
     *
     * @return A new TaskExceptionsCursor
     */
    @NonNull
    static TasksCursor fetchTasks(@NonNull final SQLiteDatabase db,
                                  final long category) {
        return (TasksCursor) db.rawQueryWithFactory(
                CURSOR_FACTORY,
                String.format(ACTIVE_TASKS_QUERY, " AND " + CKEY_CATEGORY + "=?"),
                new String[]{String.valueOf(category)}, "");
    }

    @NonNull
    static TasksCursor fetchTasks(@NonNull final SQLiteDatabase db) {

        return (TasksCursor) db.rawQueryWithFactory(CURSOR_FACTORY,
                                                    String.format(ALL_TASKS_QUERY, ""),
                                                    null, "");
    }

    @NonNull
    Date getQueuedDate() {
        if (sQueuedDateCol < 0) {
            sQueuedDateCol = getColumnIndex(CKEY_QUEUED_DATE);
        }

        Date date = DateUtils.parseDate(getString(sQueuedDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    @NonNull
    Date getRetryDate() {
        if (sRetryDateCol < 0) {
            sRetryDateCol = getColumnIndex(CKEY_RETRY_DATE);
        }

        Date date = DateUtils.parseDate(getString(sRetryDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    String getStatusCode() {
        if (sStatusCodeCol < 0) {
            sStatusCodeCol = getColumnIndex(CKEY_STATUS_CODE);
        }
        return getString(sStatusCodeCol);
    }

    /**
     * Accessor for reason field.
     *
     * @return reason
     */
    public String getReason() {
        if (sReasonCol == -1) {
            sReasonCol = getColumnIndex(CKEY_FAILURE_REASON);
        }
        return getString(sReasonCol);
    }

    /**
     * Accessor for Exception field.
     *
     * @return TaskException object
     *
     * @throws SerializationUtils.DeserializationException f
     */
    public Exception getException()
            throws SerializationUtils.DeserializationException {
        if (sExceptionCol == -1) {
            sExceptionCol = getColumnIndex(CKEY_EXCEPTION);
        }
        return (Exception) SerializationUtils.deserializeObject(getBlob(sExceptionCol));
    }

    int getNoteCount() {
        if (sNoteCountCol < 0) {
            sNoteCountCol = getColumnIndex(CKEY_EVENT_COUNT);
        }
        return getInt(sNoteCountCol);
    }

    @NonNull
    @Override
    public BindableItemCursorAdapter.BindableItem getBindableItem() {
        if (sTaskCol < 0) {
            sTaskCol = getColumnIndex(CKEY_TASK);
        }
        Task task;
        byte[] blob = getBlob(sTaskCol);
        try {
            task = SerializationUtils.deserializeObject(blob);
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            task = new LegacyTask();
        }
        task.setId(getId());
        return task;
    }

    @Override
    public long getId() {
        if (sIdCol < 0) {
            sIdCol = getColumnIndex(CKEY_PK_ID);
        }
        return getLong(sIdCol);
    }
}
