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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;
import android.database.sqlite.SQLiteStatement;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.taskqueue.Task.TaskState;
import com.eleybourn.bookcatalogue.taskqueue.TasksCursor.TaskCursorSubtype;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_CATEGORY;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_EVENT;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_EVENT_DATE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_EXCEPTION;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_FAILURE_REASON;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_ID;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_NAME;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_PRIORITY;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_QUEUE_ID;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_RETRY_COUNT;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_RETRY_DATE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_STATUS_CODE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_TASK;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.DOM_TASK_ID;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.TBL_EVENT;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.TBL_QUEUE;
import static com.eleybourn.bookcatalogue.taskqueue.DbHelper.TBL_TASK;

/**
 * Database layer. Implements all direct database access.
 *
 * @author Philip Warner
 */
public class DbAdapter {
    private final DbHelper m_dbHelper;
    private final Context m_appContext;

    /** List of statements build by this adapter so that they can be removed on close */
    private final ArrayList<SQLiteStatement> m_statements = new ArrayList<>();
    /** Static Factory object to create the custom cursor */
    private final CursorFactory m_EventsCursorFactory = new CursorFactory() {
        @Override
        public Cursor newCursor(
                SQLiteDatabase db,
                SQLiteCursorDriver masterQuery,
                String editTable,
                SQLiteQuery query) {
            return new EventsCursor(masterQuery, editTable, query);
        }
    };
    private SQLiteStatement m_checkTaskExistsStmt = null;

    /**
     * Constructor
     */
    DbAdapter(@NonNull final Context context) {
        m_appContext = context.getApplicationContext();
        m_dbHelper = new DbHelper(m_appContext);
    }

    /**
     * Get a database connection.
     *
     * @return The database
     */
    @NonNull
    protected SQLiteDatabase getDb() {
        return m_dbHelper.getWritableDatabase();
    }

    /**
     * Lookup the ID of a queue based on the name.
     *
     * @param name Queue Name
     *
     * @return The ID of the queue, 0 if no match
     */
    private long getQueueId(@NonNull final String name) {
        final String sql = "SELECT " + DOM_ID + " FROM " + TBL_QUEUE + " WHERE " + DOM_NAME + " = ?";
        SQLiteDatabase db = getDb();

        try (Cursor c = db.rawQuery(sql, new String[]{name})) {
            if (c.moveToFirst()) {
                return c.getInt(0);
            } else {
                return 0;
            }
        }
    }

    /**
     * Retrieve all queues and instantiate them for the passed QueueManager.
     * ENHANCE: Change this to only return queues with jobs of status 'Q'
     * So that queues will not be started if no jobs. Problems arise working out how to handle
     * 'waiting' jobs (if we want to allow for a 'waiting' status -- eg. waiting for network).
     *
     * @param manager Owner of the created Queue objects
     */
    void getAllQueues(@NonNull final QueueManager manager) {
        String sql = "SELECT " + DOM_NAME + " FROM " + TBL_QUEUE + " ORDER BY " + DOM_NAME;
        SQLiteDatabase db = getDb();

        try (Cursor cursor = db.rawQuery(sql, new String[]{})) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                // Create the Queue. It will register itself with its QueueManager.
                new Queue(m_appContext, manager, name);
            }
        }
    }

    /**
     * Return a ScheduledTask object for the next task that should be run from the passed
     * queue. Return NULL if no more entries.
     *
     * This method will find the highest priority RUNNABLE task, and failing that the
     * next available task.
     *
     * @param queueName Name of queue to check
     *
     * @return ScheduledTask object containing details of task
     */
    @Nullable
    ScheduledTask getNextTask(@NonNull final String queueName) {
        Date currentTime = new Date();
        long timeToNext;

        String currTimeStr = DateUtils.toSqlDateTime(currentTime);
        SQLiteDatabase db = getDb();

        String baseSql = "SELECT j.* FROM " + TBL_QUEUE + " q"
                + " JOIN " + TBL_TASK + " j ON j." + DOM_QUEUE_ID + " = q." + DOM_ID
                + " WHERE "
                + "  j." + DOM_STATUS_CODE + "= 'Q'"
                + "  AND q." + DOM_NAME + " = ?";

        // Query to check for any task that CAN run now, sorted by priority then date/id
        String canRunSql = baseSql
                + "  AND j." + DOM_RETRY_DATE + " <= ?"
                + "  ORDER BY " + DOM_PRIORITY + " ASC, " + DOM_RETRY_DATE + " ASC," + DOM_ID + " ASC"
                + " LIMIT 1";

        // Get next task that CAN RUN NOW
        Cursor c = db.rawQuery(canRunSql, new String[]{queueName, currTimeStr});
        if (!c.moveToFirst()) {
            // Close this cursor.
            c.close();
            // There is no task available now. Look for one that is waiting.
            String sql = baseSql
                    + "  AND j." + DOM_RETRY_DATE + " > ?"
                    + "  ORDER BY " + DOM_RETRY_DATE + " ASC, " + DOM_PRIORITY + " ASC, " + DOM_ID + " ASC"
                    + " LIMIT 1";
            c = db.rawQuery(sql, new String[]{queueName, currTimeStr});
        }

        try {
            // If no matching row, return NULL
            if (!c.moveToFirst()) {
                return null;
            }

            // Find task details and create ScheduledTask object
            int dateCol = c.getColumnIndex(DOM_RETRY_DATE);
            Date retryDate = DateUtils.parseDate(c.getString(dateCol));
            if (retryDate == null) {
                retryDate = new Date();
            }
            if (retryDate.after(currentTime)) {
                // set timeToNext to let called know queue is not empty
                timeToNext = (retryDate.getTime() - currentTime.getTime());
            } else {
                // No need to wait
                timeToNext = 0;
            }

            return new ScheduledTask(timeToNext, c);

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Create the specified queue if it does not exist.
     *
     * @param queueName Name of the queue
     */
    void createQueue(@NonNull final String queueName) {
        long id = getQueueId(queueName);
        if (id == 0) {
            ContentValues cv = new ContentValues();
            cv.put(DOM_NAME, queueName);
            getDb().insert(TBL_QUEUE, null, cv);
        }
    }

    /**
     * Save the passed task back to the database. The parameter must be a Task that
     * is already in the database. This method is used to preserve a task state.
     *
     * NOTE: this code must not assume the task exists. IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task The task to be saved. Must exist in database.
     */
    void updateTask(@NonNull final Task task) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
        cv.put(DOM_CATEGORY, task.getCategory());
        SQLiteDatabase db = getDb();
        db.update(TBL_TASK, cv, DOM_ID + " = " + task.getId(), new String[]{});
    }

    /**
     * Enqueue a task to be run in the specified queue.
     *
     * @param task      Task instance to save and run
     * @param queueName Queue name
     */
    void enqueueTask(@NonNull final Task task, @NonNull final String queueName) {
        long queueId = getQueueId(queueName);
        if (queueId == 0) {
            throw new RuntimeException("Queue '" + queueName + "' does not exist; unable to queue request");
        }

        ContentValues cv = new ContentValues();
        cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
        cv.put(DOM_CATEGORY, task.getCategory());
        cv.put(DOM_QUEUE_ID, queueId);
        SQLiteDatabase db = getDb();
        long jobId = db.insert(TBL_TASK, null, cv);
        task.setId(jobId);

    }

    /**
     * Mark the related task record as successfully completed.
     *
     * NOTE: this code must not assume the task exists. IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task Task object
     */
    void setTaskOk(@NonNull final Task task) {
        SQLiteDatabase db = getDb();
        String sql;

        // See if the task has any Events recorded
        sql = "SELECT COUNT(*) FROM " + TBL_EVENT + " WHERE " + DOM_TASK_ID + " = " + task.getId();
        try (Cursor cursor = db.rawQuery(sql, new String[]{})) {
            if (cursor.moveToFirst() && cursor.getLong(0) == 0) {
                // Delete successful tasks with no events
                db.delete(TBL_TASK, DOM_ID + " = " + task.getId(), new String[]{});
            } else {
                // Just mark is as successful
                sql = "UPDATE " + TBL_TASK + " SET " + DOM_STATUS_CODE + "= 'S' WHERE " + DOM_ID + " = " + task.getId();
                getDb().execSQL(sql);
            }
        }
    }

    void cleanupOldTasks(final int ageInDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -ageInDays);
        String oneWeekAgo = DateUtils.toSqlDateTime(cal.getTime());
        SQLiteDatabase db = getDb();
        String sql;

        db.beginTransaction();
        try {
            // Remove Events attached to old tasks
            sql = DOM_TASK_ID + " In ("
                    + "SELECT t." + DOM_ID + " FROM " + TBL_TASK + " t "
                    + " WHERE t." + DOM_RETRY_DATE + " < '" + oneWeekAgo + "')";
            db.delete(TBL_EVENT, sql, new String[]{});

            // Remove old Tasks
            sql = DOM_RETRY_DATE + " < '" + oneWeekAgo + "'";
            db.delete(TBL_TASK, sql, new String[]{});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        cleanupOrphans();
    }

    void cleanupOldEvents(final int ageInDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -ageInDays);
        String oneWeekAgo = DateUtils.toSqlDateTime(cal.getTime());
        SQLiteDatabase db = getDb();


        db.beginTransaction();
        try {
            db.delete(TBL_EVENT, DOM_EVENT_DATE + " < '" + oneWeekAgo + "'", new String[]{});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        cleanupOrphans();
    }

    void cleanupOrphans() {
        SQLiteDatabase db = getDb();
        db.beginTransaction();
        String sql;
        try {
            // Remove orphaned events -- should never be needed
            sql = "Not " + DOM_TASK_ID + " is NULL"
                    + " AND Not Exists(SELECT * FROM " + TBL_TASK + " t WHERE " + TBL_EVENT + "." + DOM_TASK_ID + " = t." + DOM_ID + ")";
            db.delete(TBL_EVENT, sql, new String[]{});

            // Remove orphaned tasks THAT WERE SUCCESSFUL
            sql = "Not Exists(SELECT * FROM " + TBL_EVENT + " e WHERE e." + DOM_TASK_ID + " = " + TBL_TASK + "." + DOM_ID + ")"
                    + " AND " + DOM_STATUS_CODE + " = 'S'";
            db.delete(TBL_TASK, sql, new String[]{});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Save and requeue the passed task.
     *
     * NOTE: this code must not assume the task exists. IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task task object to requeue.
     */
    void setTaskRequeue(@NonNull final Task task) {
        int waitSecs;
        if (!task.canRetry()) {
            // We have waited a lot already; just give up.
            setTaskFail(task, "Retry limit exceeded");
        } else {
            task.setState(TaskState.waiting);
            // Compute time Task can next be run
            Calendar cal = Calendar.getInstance();
            waitSecs = task.getRetryDelay();
            cal.add(Calendar.SECOND, waitSecs);
            // Convert to String
            String retryDate = DateUtils.toSqlDateTime(cal.getTime());
            // Update record
            ContentValues cv = new ContentValues();
            cv.put(DOM_RETRY_DATE, retryDate);
            cv.put(DOM_RETRY_COUNT, task.getRetries() + 1);
            cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
            getDb().update(TBL_TASK, cv, DOM_ID + " = " + task.getId(), new String[]{});
        }
    }

    /**
     * Save and mark the task as failed.
     *
     * NOTE: this code must not assume the task exists. IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task    Task that failed.
     * @param message Final message to store. Task can also contain an Exception object.
     */
    void setTaskFail(@NonNull final Task task, @NonNull final String message) {
        task.setState(TaskState.failed);

        ContentValues cv = new ContentValues();
        cv.put(DOM_FAILURE_REASON, message);
        cv.put(DOM_STATUS_CODE, "F");
        cv.put(DOM_EXCEPTION, SerializationUtils.serializeObject(task.getException()));
        cv.put(DOM_TASK, SerializationUtils.serializeObject(task));

        getDb().update(TBL_TASK, cv, DOM_ID + " = " + task.getId(), new String[]{});
    }

    /**
     * Store an Event object for later retrieval after task has completed. This is
     * analogous to writing a line to the 'log file' for the task.
     *
     * NOTE: this code must not assume the task exists. IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param t Related task
     * @param e Event (usually subclassed)
     */
    void storeTaskEvent(@NonNull final Task t, @NonNull final Event e) {
        SQLiteDatabase db = getDb();

        // Setup parameters for insert
        ContentValues cv = new ContentValues();
        cv.put(DOM_TASK_ID, t.getId());
        cv.put(DOM_EVENT, SerializationUtils.serializeObject(e));

        // Construct statements we want
        if (m_checkTaskExistsStmt == null) {
            String sql = "SELECT COUNT(*) FROM " + TBL_TASK + " WHERE " + DOM_ID + " = ?";
            m_checkTaskExistsStmt = db.compileStatement(sql);
            m_statements.add(m_checkTaskExistsStmt);
        }

        db.beginTransaction();
        try {
            // Check task exists
            m_checkTaskExistsStmt.bindLong(1, t.getId());
            long count = m_checkTaskExistsStmt.simpleQueryForLong();
            if (count > 0) {
                long eventId = db.insert(TBL_EVENT, null, cv);
                db.setTransactionSuccessful();
                e.setId(eventId);
            }
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Static method to get an Events Cursor returning all events for the passed task.
     *
     * @param db     Database
     * @param taskId ID of the task whose exceptions we want
     *
     * @return A new TaskExceptionsCursor
     */
    private EventsCursor fetchTaskEvents(@NonNull final SQLiteDatabase db, final long taskId) {
        String m_taskEventsQuery = "SELECT e.* FROM " + TBL_EVENT + " e "
                + " WHERE e." + DOM_TASK_ID + " = ? "
                + " ORDER BY e." + DOM_ID + " ASC";
        return (EventsCursor) db.rawQueryWithFactory(m_EventsCursorFactory, m_taskEventsQuery, new String[]{taskId + ""}, "");
    }

    /**
     * Static method to get an Events Cursor returning all events
     *
     * @return A new TaskExceptionsCursor
     */
    private EventsCursor fetchAllEvents(@NonNull final SQLiteDatabase db) {
        String m_eventsQuery = "SELECT * FROM " + TBL_EVENT + " ORDER BY " + DOM_ID + " ASC";
        return (EventsCursor) db.rawQueryWithFactory(m_EventsCursorFactory, m_eventsQuery, new String[]{}, "");
    }

    /**
     * Return an EventsCursor for all events related to the specified task ID.
     *
     * @param taskId ID of the task
     *
     * @return Cursor of exceptions
     */
    EventsCursor getTaskEvents(final long taskId) {
        return fetchTaskEvents(getDb(), taskId);
    }

    /**
     * Return an EventsCursor for all events.
     *
     * @return Cursor of exceptions
     */
    EventsCursor getAllEvents() {
        return fetchAllEvents(getDb());
    }

    /**
     * Return as TasksCursor for the specified type.
     *
     * @param type Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    TasksCursor getTasks(@NonNull final TaskCursorSubtype type) {
        return TasksCursor.fetchTasks(getDb(), type);
    }

    /**
     * Return as TasksCursor for the specified category and type.
     *
     * @param category Category to get
     * @param type     Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    TasksCursor getTasks(final long category,
                         @SuppressWarnings("SameParameterValue") @NonNull final TaskCursorSubtype type) {
        return TasksCursor.fetchTasks(getDb(), category, type);
    }

    /**
     * Delete the specified Event object.
     *
     * @param id ID of Event to delete.
     */
    void deleteEvent(final long id) {
        //String sql = "Delete from " + TBL_TASK_EXCEPTIONS + " Where " + DOM_ID + " = ?";
        //ContentValues cv = new ContentValues();
        //cv.put(DOM_EVENT_ID, id);
        getDb().delete(TBL_EVENT, DOM_ID + " = ?", new String[]{id + ""});
        cleanupOrphans();
    }

    /**
     * Delete the specified Task object.
     *
     * @param id ID of Task to delete.
     */
    void deleteTask(final long id) {
        SQLiteDatabase db = getDb();
        db.beginTransaction();
        try {
            String[] args = new String[]{Long.toString(id)};
            db.delete(TBL_EVENT, DOM_TASK_ID + " = ?", args);
            db.delete(TBL_TASK, DOM_ID + " = ?", new String[]{id + ""});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Generic function to close the database
     */
    public void close() {
        try {
            for (SQLiteStatement s : m_statements) {
                try {
                    s.close();
                } catch (Exception ignore) {
                }
            }
            m_dbHelper.close();

        } catch (Exception ignore) {
        } finally {
            m_statements.clear();
        }
    }

    /**
     * Class containing information about the next task to be executed in a given queue.
     *
     * @author Philip Warner
     */
    protected class ScheduledTask {
        /** Time, in milliseconds, until Task needs to be executed. */
        final long timeUntilRunnable;
        /** Blob for TAsk retrieved from DB. We do not deserialize until necessary. */
        final byte[] m_blob;
        /** Retry count retrieved from DB. */
        final int m_retries;
        /** ID of Task. */
        final int id;

        /**
         * Constructor
         *
         * @param timeUntilRunnable Milliseconds until task should be run
         * @param cursor            Cursor positioned at task details
         */
        ScheduledTask(final long timeUntilRunnable, @NonNull final Cursor cursor) {
            this.timeUntilRunnable = timeUntilRunnable;
            int taskCol = cursor.getColumnIndex(DOM_TASK);
            m_retries = cursor.getInt(cursor.getColumnIndex(DOM_RETRY_COUNT));
            this.id = cursor.getInt(cursor.getColumnIndex(DOM_ID));
            m_blob = cursor.getBlob(taskCol);
        }

        /**
         * Return the Task object after de-serialising the blob.
         *
         * @return related Task object
         */
        protected Task getTask() {
            Task task;

            // Deserialize
            try {
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(m_blob));
                Object object = in.readObject();
                task = (Task) object;
            } catch (Exception e) {
                task = null;
            }

            if (task != null) {
                task.setId(id);
                task.setRetries(m_retries);
                // Set this here so that it can be adjusted by the task when it is run.
                task.setRetryDelay();
            }

            return task;
        }
    }
}
