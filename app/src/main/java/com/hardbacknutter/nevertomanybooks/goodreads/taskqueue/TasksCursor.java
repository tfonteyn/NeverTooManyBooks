/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.goodreads.taskqueue;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;

import androidx.annotation.NonNull;

import java.util.Date;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.SerializationUtils;

import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_CATEGORY;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_EVENT_COUNT;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_EXCEPTION;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_FAILURE_REASON;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_ID;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_QUEUED_DATE;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_RETRY_DATE;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_STATUS_CODE;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_TASK;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_TASK_ID;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.TBL_EVENT;
import static com.hardbacknutter.nevertomanybooks.goodreads.taskqueue.TaskQueueDBHelper.TBL_TASK;

/**
 * Cursor subclass used to make accessing Tasks a little easier.
 */
public final class TasksCursor
        extends SQLiteCursor
        implements BindableItemCursor {

    /** Static Factory object to create the custom cursor. */
    private static final CursorFactory CURSOR_FACTORY =
            (db, masterQuery, editTable, query) -> new TasksCursor(masterQuery, editTable, query);

    private static final String ALL_TASKS_QUERY =
            "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e"
            + " WHERE e." + DOM_TASK_ID + "=t." + DOM_ID
            + ") AS " + DOM_EVENT_COUNT
            + " FROM " + TBL_TASK + " t WHERE 1=1 %1$s"
            + " ORDER BY " + DOM_ID + " DESC";

    private static final String ACTIVE_TASKS_QUERY =
            "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e"
            + " WHERE e." + DOM_TASK_ID + "=t." + DOM_ID
            + ") AS " + DOM_EVENT_COUNT
            + " FROM " + TBL_TASK + " t "
            + " WHERE NOT " + DOM_STATUS_CODE
            + " IN ('S','F') %1$s"
            + " ORDER BY " + DOM_ID + " DESC";

    /** Column number of ID column. */
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
                String.format(ACTIVE_TASKS_QUERY, " AND " + DOM_CATEGORY + "=?"),
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
            sQueuedDateCol = getColumnIndex(DOM_QUEUED_DATE);
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
            sRetryDateCol = getColumnIndex(DOM_RETRY_DATE);
        }

        Date date = DateUtils.parseDate(getString(sRetryDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    String getStatusCode() {
        if (sStatusCodeCol < 0) {
            sStatusCodeCol = getColumnIndex(DOM_STATUS_CODE);
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
            sReasonCol = getColumnIndex(DOM_FAILURE_REASON);
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
            sExceptionCol = getColumnIndex(DOM_EXCEPTION);
        }
        return (Exception) SerializationUtils.deserializeObject(getBlob(sExceptionCol));
    }

    int getNoteCount() {
        if (sNoteCountCol < 0) {
            sNoteCountCol = getColumnIndex(DOM_EVENT_COUNT);
        }
        return getInt(sNoteCountCol);
    }

    @NonNull
    @Override
    public BindableItemCursorAdapter.BindableItem getBindableItem() {
        if (sTaskCol < 0) {
            sTaskCol = getColumnIndex(DOM_TASK);
        }
        Task task;
        byte[] blob = getBlob(sTaskCol);
        try {
            task = SerializationUtils.deserializeObject(blob);
        } catch (@NonNull final SerializationUtils.DeserializationException de) {
            task = new LegacyTask(App.getFakeUserContext());
        }
        task.setId(getId());
        return task;
    }

    @Override
    public long getId() {
        if (sIdCol < 0) {
            sIdCol = getColumnIndex(DOM_ID);
        }
        return getLong(sIdCol);
    }
}
