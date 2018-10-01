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

package com.eleybourn.bookcatalogue.taskqueue;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.widgets.BindableItemCursorAdapter;

import java.util.Date;

import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_CATEGORY;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_EVENT_COUNT;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_ID;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_QUEUED_DATE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_RETRY_DATE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_STATUS_CODE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_TASK;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_TASK_ID;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.TBL_EVENT;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.TBL_TASK;
import static com.eleybourn.bookcatalogue.utils.SerializationUtils.deserializeObject;

/**
 * Cursor subclass used to make accessing Tasks a little easier.
 *
 * @author Philip Warner
 */
public class TasksCursor extends SQLiteCursor implements BindableItemCursor {

    /** Static Factory object to create the custom cursor */
    private static final CursorFactory m_factory = new CursorFactory() {
        @Override
        public Cursor newCursor(SQLiteDatabase db,
                                SQLiteCursorDriver masterQuery, String editTable,
                                SQLiteQuery query) {
            return new TasksCursor(masterQuery, editTable, query);
        }
    };

    private static final String m_failedTasksQuery = "Select *, "
            + " (Select Count(*) from " + TBL_EVENT + " e Where e." + DOM_TASK_ID + "=t." + DOM_ID + ") as " + DOM_EVENT_COUNT
            + " From " + TBL_TASK + " t "
            + " Where " + DOM_STATUS_CODE + " = 'F' %1$s Order by " + DOM_ID + " desc";

    private static final String m_allTasksQuery = "Select *, "
            + " (Select Count(*) from " + TBL_EVENT + " e Where e." + DOM_TASK_ID + "=t." + DOM_ID + ") as " + DOM_EVENT_COUNT
            + " From " + TBL_TASK + " t Where 1 = 1 %1$s"
            + " Order by " + DOM_ID + " desc";

    private static final String m_activeTasksQuery = "Select *, "
            + " (Select Count(*) from " + TBL_EVENT + " e Where e." + DOM_TASK_ID + "=t." + DOM_ID + ") as " + DOM_EVENT_COUNT
            + " From " + TBL_TASK + " t "
            + " Where Not " + DOM_STATUS_CODE + " In ('S','F') %1$s Order by " + DOM_ID + " desc";

    private static final String m_queuedTasksQuery = "Select *, "
            + " (Select Count(*) from " + TBL_EVENT + " e Where e." + DOM_TASK_ID + "=t." + DOM_ID + ") as " + DOM_EVENT_COUNT
            + " From " + TBL_TASK + " t "
            + " Where " + DOM_STATUS_CODE + " = 'Q' %1$s Order by " + DOM_ID + " desc";
    /** Column number of ID column. */
    private static int m_idCol = -1;

    /** Column number of date column. */
    private static int m_queuedDateCol = -1;
    /** Column number of retry date column. */
    private static int m_retryDateCol = -1;
    /** Column number of retry count column. */
    private static int m_statusCodeCol = -1;
    /** Column number of Exception column. */
    private static int m_taskCol = -2;
    /** Column number of NoteCount column. */
    private static int m_noteCountCol = -1;

    /**
     * Constructor, based on SQLiteCursor constructor
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
    static TasksCursor fetchTasks(@NonNull final SQLiteDatabase db,
                                  final long category,
                                  @NonNull final TaskCursorSubtype type) {
        String query;
        switch (type) {
            case all:
                query = m_allTasksQuery;
                break;
            case queued:
                query = m_queuedTasksQuery;
                break;
            case failed:
                query = m_failedTasksQuery;
                break;
            case active:
                query = m_activeTasksQuery;
                break;
            default:
                throw new RuntimeException("Unexpected cursor subtype specified: " + type);
        }
        // Add extra 'where' clause
        query = String.format(query, " and " + DOM_CATEGORY + " = " + category);
        return (TasksCursor) db.rawQueryWithFactory(m_factory, query, new String[]{}, "");
    }

    static TasksCursor fetchTasks(SQLiteDatabase db, TaskCursorSubtype type) {
        String query;
        switch (type) {
            case all:
                query = m_allTasksQuery;
                break;
            case queued:
                query = m_queuedTasksQuery;
                break;
            case failed:
                query = m_failedTasksQuery;
                break;
            case active:
                query = m_activeTasksQuery;
                break;
            default:
                throw new RuntimeException("Unexpected cursor subtype specified: " + type);
        }
        // No extra 'where' clause
        query = String.format(query, "");
        return (TasksCursor) db.rawQueryWithFactory(m_factory, query, new String[]{}, "");
    }

    @Override
    public long getId() {
        if (m_idCol == -1) {
            m_idCol = this.getColumnIndex(DOM_ID);
        }
        return getLong(m_idCol);
    }

    public Date getQueuedDate() {
        if (m_queuedDateCol == -1) {
            m_queuedDateCol = this.getColumnIndex(DOM_QUEUED_DATE);
        }

        Date date = DateUtils.parseDate(getString(m_queuedDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;
    }

    public Date getRetryDate() {
        if (m_retryDateCol == -1) {
            m_retryDateCol = this.getColumnIndex(DOM_RETRY_DATE);
        }

        Date date = DateUtils.parseDate(getString(m_retryDateCol));
        if (date == null) {
            date = new Date();
        }
        return date;


    }

    public String getStatusCode() {
        if (m_statusCodeCol == -1) {
            m_statusCodeCol = this.getColumnIndex(DOM_STATUS_CODE);
        }
        return getString(m_statusCodeCol);
    }

    private Task getTask() {
        if (m_taskCol == -2) {
            m_taskCol = this.getColumnIndex(DOM_TASK);
        }
        Task t;
        byte[] blob = getBlob(m_taskCol);
        try {
            t = deserializeObject(blob);
        } catch (SerializationUtils.DeserializationException de) {
            t = QueueManager.getQueueManager().newLegacyTask();
        }
        t.setId(this.getId());
        return t;
    }

    public int getNoteCount() {
        if (m_noteCountCol == -1) {
            m_noteCountCol = this.getColumnIndex(DOM_EVENT_COUNT);
        }
        return getInt(m_noteCountCol);
    }

    @Override
    public BindableItemCursorAdapter.BindableItem getBindableItem() {
        return getTask();
    }

    public enum TaskCursorSubtype {all, failed, active, queued}
}
