/*
 * @Copyright 2019 HardBackNutter
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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SerializationUtils;

import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_CATEGORY;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_EVENT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_EVENT_DATE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_EXCEPTION;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_FAILURE_REASON;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_NAME;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_PRIORITY;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_QUEUE_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_RETRY_COUNT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_RETRY_DATE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_STATUS_CODE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_TASK;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.DOM_TASK_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.TBL_EVENT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.TBL_QUEUE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDBHelper.TBL_TASK;

/**
 * Database layer. Provides all direct database access.
 */
class TaskQueueDAO {

    private static final String SQL_GET_QUEUE_ID =
            "SELECT " + DOM_ID + " FROM " + TBL_QUEUE + " WHERE " + DOM_NAME + "=?";
    private static final String SQL_GET_ALL_QUEUES =
            "SELECT " + DOM_NAME + " FROM " + TBL_QUEUE + " ORDER BY " + DOM_NAME;
    private static final String SQL_COUNT_EVENTS =
            "SELECT COUNT(*) FROM " + TBL_EVENT + " WHERE " + DOM_TASK_ID + "=?";

    @NonNull
    private final TaskQueueDBHelper mTaskQueueDBHelper;
    /** List of statements build by this adapter so that they can be removed on close. */
    private final Collection<SQLiteStatement> mStatements = new ArrayList<>();
    /** Static Factory object to create the custom cursor. */
    private final CursorFactory mEventsCursorFactory = (db, masterQuery, editTable, query) ->
                                                               new EventsCursor(masterQuery,
                                                                                editTable,
                                                                                query);
    private SQLiteStatement mCheckTaskExistsStmt;

    /**
     * Constructor.
     */
    TaskQueueDAO() {
        mTaskQueueDBHelper = new TaskQueueDBHelper(App.getAppContext());
    }

    /**
     * Get a database connection.
     *
     * @return The database
     */
    @NonNull
    SQLiteDatabase getDb() {
        return mTaskQueueDBHelper.getWritableDatabase();
    }

    /**
     * Lookup the id of a queue based on the name.
     *
     * @param name Queue Name
     *
     * @return The id of the queue, 0 if no match
     */
    private long getQueueId(@NonNull final String name) {
        try (Cursor cursor = getDb().rawQuery(SQL_GET_QUEUE_ID, new String[]{name})) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                return 0;
            }
        }
    }

    /**
     * Retrieve all queues and instantiate them for the passed QueueManager.
     *
     * @param queueManager Owner of the created Queue objects
     */
    void getAllQueues(@NonNull final QueueManager queueManager) {
        try (Cursor cursor = getDb().rawQuery(SQL_GET_ALL_QUEUES, null)) {
            while (cursor.moveToNext()) {
                // Create the Queue. It will register itself with its QueueManager.
                new Queue(queueManager, cursor.getString(0));
            }
        }
    }

    /**
     * Return a ScheduledTask object for the next task that should be run from the passed
     * queue. Return {@code null} if no more entries.
     * <p>
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
        String currTimeStr = DateUtils.utcSqlDate(currentTime);

        SQLiteDatabase db = getDb();

        String baseSql = "SELECT j.* FROM " + TBL_QUEUE + " q"
                         + " JOIN " + TBL_TASK + " j ON j." + DOM_QUEUE_ID + " = q." + DOM_ID
                         + " WHERE j." + DOM_STATUS_CODE + "= 'Q'  AND q." + DOM_NAME + "=?";

        // Query to check for any task that CAN run now, sorted by priority then date/id
        String canRunSql = baseSql
                           + "  AND j." + DOM_RETRY_DATE + " <= ?"
                           + "  ORDER BY "
                           + DOM_PRIORITY + " ASC, " + DOM_RETRY_DATE + " ASC," + DOM_ID + " ASC"
                           + " LIMIT 1";

        // Get next task that CAN RUN NOW
        Cursor cursor = db.rawQuery(canRunSql, new String[]{queueName, currTimeStr});
        if (!cursor.moveToFirst()) {
            // Close this cursor.
            cursor.close();
            // There is no task available now. Look for one that is waiting.
            String sql = baseSql
                         + "  AND j." + DOM_RETRY_DATE + " > ?"
                         + "  ORDER BY "
                         + DOM_RETRY_DATE + " ASC, " + DOM_PRIORITY + " ASC, " + DOM_ID + " ASC"
                         + " LIMIT 1";
            cursor = db.rawQuery(sql, new String[]{queueName, currTimeStr});
        }

        try {
            if (!cursor.moveToFirst()) {
                return null;
            }

            // Find task details and create ScheduledTask object
            int dateCol = cursor.getColumnIndex(DOM_RETRY_DATE);
            Date retryDate = DateUtils.parseDate(cursor.getString(dateCol));
            if (retryDate == null) {
                retryDate = new Date();
            }

            long timeToNext;
            if (retryDate.after(currentTime)) {
                // set timeToNext to let called know queue is not empty
                timeToNext = retryDate.getTime() - currentTime.getTime();
            } else {
                // No need to wait
                timeToNext = 0;
            }

            return new ScheduledTask(timeToNext, cursor);

        } finally {
            if (cursor != null) {
                cursor.close();
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
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task The task to be saved. Must exist in database.
     */
    void updateTask(@NonNull final Task task) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
        cv.put(DOM_CATEGORY, task.getCategory());
        getDb().update(TBL_TASK, cv, DOM_ID + "=?",
                       new String[]{String.valueOf(task.getId())});
    }

    /**
     * Enqueue a task to be run in the specified queue.
     *
     * @param task      Task instance to save and run
     * @param queueName Queue name
     */
    void enqueueTask(@NonNull final Task task,
                     @NonNull final String queueName) {
        long queueId = getQueueId(queueName);
        if (queueId == 0) {
            throw new IllegalArgumentException("Queue '" + queueName + "' does not exist");
        }

        ContentValues cv = new ContentValues();
        cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
        cv.put(DOM_CATEGORY, task.getCategory());
        cv.put(DOM_QUEUE_ID, queueId);
        long jobId = getDb().insert(TBL_TASK, null, cv);
        task.setId(jobId);

    }

    /**
     * Set the related task record as successfully completed.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task Task object
     */
    void setTaskOk(@NonNull final Task task) {
        SQLiteDatabase db = getDb();

        // See if the task has any Events recorded
        try (Cursor cursor = db.rawQuery(SQL_COUNT_EVENTS,
                                         new String[]{String.valueOf(task.getId())})) {
            if (cursor.moveToFirst() && cursor.getLong(0) == 0) {
                // Delete successful tasks with no events
                db.delete(TBL_TASK, DOM_ID + " =?",
                          new String[]{String.valueOf(task.getId())});
            } else {
                // Just set is as successful
                db.execSQL("UPDATE " + TBL_TASK + " SET " + DOM_STATUS_CODE + "= 'S'"
                           + " WHERE " + DOM_ID + '=' + task.getId());
            }
        }
    }

    void cleanupOldTasks() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -7);
        String oneWeekAgo = DateUtils.utcSqlDateTime(cal.getTime());
        SQLiteDatabase db = getDb();


        db.beginTransaction();
        try {
            // Remove Events attached to old tasks
            String whereClause = DOM_TASK_ID + " IN ("
                                 + "SELECT t." + DOM_ID + " FROM " + TBL_TASK + " t "
                                 + " WHERE t." + DOM_RETRY_DATE + " < ?)";
            db.delete(TBL_EVENT, whereClause, new String[]{oneWeekAgo});

            // Remove old Tasks
            db.delete(TBL_TASK, DOM_RETRY_DATE + " < ?", new String[]{oneWeekAgo});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        cleanupOrphans();
    }

    void cleanupOldEvents() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -7);
        String oneWeekAgo = DateUtils.utcSqlDateTime(cal.getTime());
        SQLiteDatabase db = getDb();


        db.beginTransaction();
        try {
            db.delete(TBL_EVENT, DOM_EVENT_DATE + " < ?", new String[]{oneWeekAgo});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        cleanupOrphans();
    }

    void cleanupOrphans() {
        SQLiteDatabase db = getDb();
        db.beginTransaction();
        try {
            // Remove orphaned events -- should never be needed
            String whereClause = "NOT " + DOM_TASK_ID + " IS NULL"
                                 + " AND NOT EXISTS(SELECT * FROM " + TBL_TASK + " t"
                                 + " WHERE " + TBL_EVENT + '.' + DOM_TASK_ID + "=t." + DOM_ID + ')';
            db.delete(TBL_EVENT, whereClause, null);

            // Remove orphaned tasks THAT WERE SUCCESSFUL
            whereClause = "NOT EXISTS(SELECT * FROM " + TBL_EVENT + " e"
                          + " WHERE e." + DOM_TASK_ID + '=' + TBL_TASK + '.' + DOM_ID + ')'
                          + " AND " + DOM_STATUS_CODE + " = 'S'";
            db.delete(TBL_TASK, whereClause, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Save and requeue the passed task.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task task object to requeue.
     */
    void setTaskRequeue(@NonNull final Task task) {
        if (!task.canRetry()) {
            // We have waited a lot already; just give up.
            setTaskFail(task, "Retry limit exceeded");
        } else {
            // Compute time Task can next be run
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, task.getRetryDelay());
            // Update record
            ContentValues cv = new ContentValues();
            cv.put(DOM_RETRY_DATE, DateUtils.utcSqlDateTime(cal.getTime()));
            cv.put(DOM_RETRY_COUNT, task.getRetries() + 1);
            cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
            getDb().update(TBL_TASK, cv, DOM_ID + "=?",
                           new String[]{String.valueOf(task.getId())});
        }
    }

    /**
     * Save and set the task as failed.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task    Task that failed.
     * @param message Final message to store. Task can also contain an Exception object.
     */
    void setTaskFail(@NonNull final Task task,
                     @NonNull final String message) {
        ContentValues cv = new ContentValues();
        cv.put(DOM_FAILURE_REASON, message);
        cv.put(DOM_STATUS_CODE, "F");
        cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
        Exception e = task.getException();
        if (e != null) {
            cv.put(DOM_EXCEPTION, SerializationUtils.serializeObject(e));
        }

        getDb().update(TBL_TASK, cv, DOM_ID + "=?",
                       new String[]{String.valueOf(task.getId())});
    }

    /**
     * Store an Event object for later retrieval after task has completed. This is
     * analogous to writing a line to the 'log file' for the task.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     */
    void storeTaskEvent(@NonNull final Task task,
                        @NonNull final Event event) {
        SQLiteDatabase db = getDb();

        // Setup parameters for insert
        ContentValues cv = new ContentValues();
        cv.put(DOM_TASK_ID, task.getId());
        cv.put(DOM_EVENT, SerializationUtils.serializeObject(event));

        // Construct statements we want
        if (mCheckTaskExistsStmt == null) {
            String sql = "SELECT COUNT(*) FROM " + TBL_TASK + " WHERE " + DOM_ID + "=?";
            mCheckTaskExistsStmt = db.compileStatement(sql);
            mStatements.add(mCheckTaskExistsStmt);
        }

        db.beginTransaction();
        try {
            // Check task exists
            mCheckTaskExistsStmt.bindLong(1, task.getId());
            long count = mCheckTaskExistsStmt.simpleQueryForLong();
            if (count > 0) {
                long eventId = db.insert(TBL_EVENT, null, cv);
                db.setTransactionSuccessful();
                event.setId(eventId);
            }
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Get an Events Cursor returning all events for the passed task.
     *
     * @param taskId id of the task whose exceptions we want
     *
     * @return A new EventsCursor
     */
    @NonNull
    EventsCursor getTaskEvents(final long taskId) {
        String sql = "SELECT e.* FROM " + TBL_EVENT + " e "
                     + " WHERE e." + DOM_TASK_ID + "=?"
                     + " ORDER BY e." + DOM_ID + " ASC";
        return (EventsCursor) getDb().rawQueryWithFactory(mEventsCursorFactory, sql,
                                                          new String[]{String.valueOf(taskId)}, "");
    }

    /**
     * @return EventsCursor returning all events.
     */
    @NonNull
    EventsCursor getAllEvents() {
        String sql = "SELECT * FROM " + TBL_EVENT + " ORDER BY " + DOM_ID + " ASC";
        return (EventsCursor) getDb().rawQueryWithFactory(mEventsCursorFactory, sql, null, "");
    }

    /**
     * @return TasksCursor returning all tasks.
     */
    @NonNull
    TasksCursor getTasks() {
        return TasksCursor.fetchTasks(getDb());
    }

    /**
     * Return as TasksCursor for the active tasks of the specified category.
     *
     * @param category Category to get
     *
     * @return Cursor of exceptions
     */
    @NonNull
    TasksCursor getTasks(final long category) {
        return TasksCursor.fetchTasks(getDb(), category);
    }

    /**
     * Delete the specified Event object.
     *
     * @param id of Event to delete.
     */
    void deleteEvent(final long id) {
        getDb().delete(TBL_EVENT, DOM_ID + "=?", new String[]{String.valueOf(id)});
        cleanupOrphans();
    }

    /**
     * Delete the specified Task object.
     *
     * @param id of Task to delete.
     */
    void deleteTask(final long id) {
        SQLiteDatabase db = getDb();
        db.beginTransaction();
        try {
            db.delete(TBL_EVENT, DOM_TASK_ID + "=?", new String[]{String.valueOf(id)});
            db.delete(TBL_TASK, DOM_ID + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Generic function to close the database.
     */
    public void close() {
        try {
            for (SQLiteStatement s : mStatements) {
                try {
                    s.close();
                } catch (@NonNull final RuntimeException ignore) {
                }
            }
            mTaskQueueDBHelper.close();
        } catch (@NonNull final RuntimeException ignore) {
        } finally {
            mStatements.clear();
        }
    }

    /**
     * Class containing information about the next task to be executed in a given queue.
     */
    static class ScheduledTask {

        /** Time, in milliseconds, until Task needs to be executed. */
        final long timeUntilRunnable;
        /** Blob for Task retrieved from DB. We do not deserializeObject until necessary. */
        final byte[] mBlob;
        /** Retry count retrieved from DB. */
        final int mRetries;
        /** id of Task. */
        final int id;

        /**
         * Constructor.
         *
         * @param timeUntilRunnable Milliseconds until task should be run
         * @param cursor            Cursor positioned at task details
         */
        ScheduledTask(final long timeUntilRunnable,
                      @NonNull final Cursor cursor) {
            this.timeUntilRunnable = timeUntilRunnable;
            mRetries = cursor.getInt(cursor.getColumnIndex(DOM_RETRY_COUNT));
            id = cursor.getInt(cursor.getColumnIndex(DOM_ID));
            mBlob = cursor.getBlob(cursor.getColumnIndex(DOM_TASK));
        }

        /**
         * Return the Task object after de-serialising the blob.
         *
         * @return related Task object
         */
        @Nullable
        Task getTask() {
            Task task;

            try {
                task = SerializationUtils.deserializeObject(mBlob);
            } catch (@NonNull final SerializationUtils.DeserializationException e) {
                return null;
            }

            task.setId(id);
            task.setRetries(mRetries);
            // Set this here so that it can be adjusted by the task when it is run.
            task.setRetryDelay();

            return task;
        }
    }
}
