/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.tasks.taskqueue;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.RTE;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.adapters.BindableItemCursorAdapter;

import java.util.Date;

import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_CATEGORY;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_EVENT_COUNT;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_EXCEPTION;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_FAILURE_REASON;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_ID;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_QUEUED_DATE;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_QUEUE_ID;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_RETRY_DATE;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_STATUS_CODE;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_TASK;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.DOM_TASK_ID;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.TBL_EVENT;
import static com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueDBHelper.TBL_TASK;

/**
 * Cursor subclass used to make accessing Tasks a little easier.
 *
 * @author Philip Warner
 */
public class TasksCursor extends SQLiteCursor implements BindableItemCursor {

    /** Static Factory object to create the custom cursor */
    private static final CursorFactory mFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(SQLiteDatabase db,
                                @NonNull SQLiteCursorDriver masterQuery, @NonNull String editTable,
                                @NonNull SQLiteQuery query) {
            return new TasksCursor(masterQuery, editTable, query);
        }
    };

    private static final String mFailedTasksQuery = "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e WHERE e." + DOM_TASK_ID + "=t." + DOM_ID + ") AS " + DOM_EVENT_COUNT
            + " FROM " + TBL_TASK + " t "
            + " WHERE " + DOM_STATUS_CODE + " = 'F' %1$s ORDER BY " + DOM_ID + " DESC";

    private static final String mAllTasksQuery = "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e WHERE e." + DOM_TASK_ID + "=t." + DOM_ID + ") AS " + DOM_EVENT_COUNT
            + " FROM " + TBL_TASK + " t WHERE 1 = 1 %1$s"
            + " ORDER BY " + DOM_ID + " DESC";

    private static final String mActiveTasksQuery = "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e WHERE e." + DOM_TASK_ID + "=t." + DOM_ID + ") AS " + DOM_EVENT_COUNT
            + " FROM " + TBL_TASK + " t "
            + " WHERE Not " + DOM_STATUS_CODE + " In ('S','F') %1$s ORDER BY " + DOM_ID + " DESC";

    private static final String mQueuedTasksQuery = "SELECT *, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e WHERE e." + DOM_TASK_ID + "=t." + DOM_ID + ") AS " + DOM_EVENT_COUNT
            + " FROM " + TBL_TASK + " t "
            + " WHERE " + DOM_STATUS_CODE + " = 'Q' %1$s ORDER BY " + DOM_ID + " DESC";
    /** Column number of ID column. */
    private static int mIdCol = -1;

    /** Column number of date column. */
    private static int mQueuedDateCol = -2;
    /** Column number of Queue_id column. */
    private static int mQueueIdCol = -2;
    /** Column number of retry date column. */
    private static int mRetryDateCol = -2;
    /** Column number of retry count column. */
    private static int mStatusCodeCol = -2;
    /** Column number of Exception column. */
    private static int mTaskCol = -2;
    /** Column number of NoteCount column. */
    private static int mNoteCountCol = -2;
    /** Column number of reason column. */
    private static int mReasonCol = -2;
    /** Column number of Exception column. */
    private static int mExceptionCol = -2;
    /**
     * Constructor, based on SQLiteCursor constructor
     */
    private TasksCursor(final @NonNull SQLiteCursorDriver driver,
                        final @NonNull String editTable,
                        final @NonNull SQLiteQuery query) {
        super(driver, editTable, query);
    }

    /**
     * Static method to get a TaskExceptions Cursor.
     *
     * @return A new TaskExceptionsCursor
     */
    @NonNull
    static TasksCursor fetchTasks(final @NonNull SQLiteDatabase db,
                                  final long category,
                                  final @NonNull TaskCursorSubtype type) {
        String query;
        switch (type) {
            case all:
                query = mAllTasksQuery;
                break;
            case queued:
                query = mQueuedTasksQuery;
                break;
            case failed:
                query = mFailedTasksQuery;
                break;
            case active:
                query = mActiveTasksQuery;
                break;
            default:
                throw new RTE.IllegalTypeException(type.toString());
        }
        // Add extra 'where' clause
        query = String.format(query, " AND " + DOM_CATEGORY + "=?");
        return (TasksCursor) db.rawQueryWithFactory(mFactory, query, new String[]{Long.toString(category)}, "");
    }

    @NonNull
    static TasksCursor fetchTasks(final @NonNull SQLiteDatabase db, final @NonNull TaskCursorSubtype type) {
        String query;
        switch (type) {
            case all:
                query = mAllTasksQuery;
                break;
            case queued:
                query = mQueuedTasksQuery;
                break;
            case failed:
                query = mFailedTasksQuery;
                break;
            case active:
                query = mActiveTasksQuery;
                break;
            default:
                throw new RTE.IllegalTypeException(type.toString());
        }
        // No extra 'where' clause
        query = String.format(query, "");
        return (TasksCursor) db.rawQueryWithFactory(mFactory, query, null, "");
    }

    @Override
    public long getId() {
        if (mIdCol < 0) {
            mIdCol = this.getColumnIndex(DOM_ID);
        }
        return getLong(mIdCol);
    }

    public long getQueueId() {
        if (mQueueIdCol == -1)
            mQueueIdCol = this.getColumnIndex(DOM_QUEUE_ID);
        return getLong(mQueueIdCol);
    }

    @NonNull
    Date getQueuedDate() {
        if (mQueuedDateCol < 0) {
            mQueuedDateCol = this.getColumnIndex(DOM_QUEUED_DATE);
        }

        Date date = DateUtils.parseDate(getString(mQueuedDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    @NonNull
    Date getRetryDate() {
        if (mRetryDateCol < 0) {
            mRetryDateCol = this.getColumnIndex(DOM_RETRY_DATE);
        }

        Date date = DateUtils.parseDate(getString(mRetryDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    String getStatusCode() {
        if (mStatusCodeCol < 0) {
            mStatusCodeCol = this.getColumnIndex(DOM_STATUS_CODE);
        }
        return getString(mStatusCodeCol);
    }

    /**
     * Accessor for reason field.
     *
     * @return reason
     */
    public String getReason() {
        if (mReasonCol == -1)
            mReasonCol = this.getColumnIndex(DOM_FAILURE_REASON);
        return getString(mReasonCol);
    }

    /**
     * Accessor for Exception field.
     *
     * @return TaskException object
     * @throws  RTE.DeserializationException f
     */
    public Exception getException() throws RTE.DeserializationException {
        if (mExceptionCol == -1)
            mExceptionCol = this.getColumnIndex(DOM_EXCEPTION);
        return (Exception) SerializationUtils.deserializeObject(getBlob(mExceptionCol));
    }

    @NonNull
    private Task getTask() {
        if (mTaskCol < 0) {
            mTaskCol = this.getColumnIndex(DOM_TASK);
        }
        Task task;
        byte[] blob = getBlob(mTaskCol);
        try {
            task = SerializationUtils.deserializeObject(blob);
        } catch (RTE.DeserializationException de) {
            task = QueueManager.getQueueManager().newLegacyTask(blob);
        }
        task.setId(this.getId());
        return task;
    }

    int getNoteCount() {
        if (mNoteCountCol < 0) {
            mNoteCountCol = this.getColumnIndex(DOM_EVENT_COUNT);
        }
        return getInt(mNoteCountCol);
    }

    @NonNull
    @Override
    public BindableItemCursorAdapter.BindableItem getBindableItem() {
        return getTask();
    }

    public enum TaskCursorSubtype {all, failed, active, queued}
}
