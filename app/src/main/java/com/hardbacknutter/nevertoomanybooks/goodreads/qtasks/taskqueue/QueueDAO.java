/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;

import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_EVENT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_EVENT_COUNT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_EVENT_UTC_DATETIME;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_QUEUE_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_QUEUE_NAME;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_CATEGORY;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_EXCEPTION;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_FAILURE_REASON;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_ID;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_PRIORITY;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_RETRY_COUNT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_RETRY_UTC_DATETIME;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.KEY_TASK_STATUS_CODE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.TBL_EVENT;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.TBL_QUEUE;
import static com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper.TBL_TASK;

/**
 * Database layer. Provides all direct database access.
 */
class QueueDAO
        implements AutoCloseable {

    /** Long. */
    private static final String SQL_COUNT_ACTIVE_TASKS =
            "SELECT COUNT(*) FROM " + TBL_TASK
            + " WHERE NOT " + KEY_TASK_STATUS_CODE + " IN ("
            + "'" + TQTask.COMPLETED + "','" + TQTask.FAILED + "')"
            + " AND " + KEY_TASK_CATEGORY + "=?";

    private static final String SQL_FETCH_ALL_TASKS =
            "SELECT t.*, "
            + " (SELECT COUNT(*) FROM " + TBL_EVENT + " e"
            + /* */ " WHERE e." + KEY_TASK_ID + "=t." + KEY_PK_ID
            + ") AS " + KEY_EVENT_COUNT
            + " FROM " + TBL_TASK + " t"
            + " ORDER BY t." + KEY_PK_ID + " DESC";

    private static final String SQL_FETCH_ALL_EVENTS =
            "SELECT * FROM " + TBL_EVENT
            + " ORDER BY " + KEY_PK_ID;

    private static final String SQL_FETCH_EVENTS_FOR_TASK =
            "SELECT * FROM " + TBL_EVENT + " WHERE " + KEY_TASK_ID + "=?"
            + " ORDER BY " + KEY_PK_ID;

    private static final String SQL_GET_QUEUE_ID =
            "SELECT " + KEY_PK_ID + " FROM " + TBL_QUEUE + " WHERE " + KEY_QUEUE_NAME + "=?";

    private static final String SQL_FETCH_ALL_QUEUES =
            "SELECT " + KEY_QUEUE_NAME + " FROM " + TBL_QUEUE + " ORDER BY " + KEY_QUEUE_NAME;

    /** Long. */
    private static final String SQL_COUNT_EVENTS =
            "SELECT COUNT(*) FROM " + TBL_EVENT + " WHERE " + KEY_TASK_ID + "=?";

    private static final String SQL_NEXT_TASK_BASE =
            "SELECT t.* FROM " + TBL_QUEUE + " q"
            + " JOIN " + TBL_TASK + " t ON t." + KEY_QUEUE_ID + " = q." + KEY_PK_ID
            + " WHERE t." + KEY_TASK_STATUS_CODE + "= '" + TQTask.QUEUED + "'"
            + " AND q." + KEY_QUEUE_NAME + "=?";

    /** Query to check for the highest priority task that can run now. */
    private static final String SQL_TASK_WHICH_CAN_RUN_NOW =
            SQL_NEXT_TASK_BASE + " AND t." + KEY_TASK_RETRY_UTC_DATETIME + "<=?"
            + " ORDER BY " + KEY_TASK_PRIORITY + ',' + KEY_TASK_RETRY_UTC_DATETIME + ',' + KEY_PK_ID
            + " LIMIT 1";

    /** Query to check for the first task that is waiting to run. */
    private static final String SQL_TASK_WHICH_IS_WAITING =
            SQL_NEXT_TASK_BASE + " AND t." + KEY_TASK_RETRY_UTC_DATETIME + ">?"
            + " ORDER BY " + KEY_TASK_RETRY_UTC_DATETIME + ',' + KEY_TASK_PRIORITY + ',' + KEY_PK_ID
            + " LIMIT 1";

    /** Remove orphaned tasks THAT WERE SUCCESSFUL. */
    private static final String SQL_DELETE_COMPLETED_TASKS =
            "DELETE FROM " + TBL_TASK + " WHERE "
            + KEY_TASK_STATUS_CODE + " = '" + TQTask.COMPLETED + "'"
            + " AND NOT EXISTS(SELECT * FROM " + TBL_EVENT + " e"
            + /* */ " WHERE e." + KEY_TASK_ID + '=' + TBL_TASK + '.' + KEY_PK_ID + ')';

    /** Remove orphaned events -- should never be needed. */
    private static final String SQL_DELETE_ORPHANED_EVENTS =
            "DELETE FROM " + TBL_EVENT + " WHERE "
            + KEY_TASK_ID + " IS NOT NULL"
            + " AND NOT EXISTS(SELECT * FROM " + TBL_TASK + " t"
            + /* */ " WHERE " + TBL_EVENT + '.' + KEY_TASK_ID + "=t." + KEY_PK_ID + ')';

    /** Remove Events attached to old tasks. */
    private static final String SQL_DELETE_OLD_EVENTS_WITH_TASKS =
            "DELETE FROM " + TBL_EVENT + " WHERE " + KEY_TASK_ID + " IN ("
            + "SELECT " + KEY_PK_ID + " FROM " + TBL_TASK + " WHERE " + KEY_TASK_RETRY_UTC_DATETIME
            + "<?)";

    /** Remove old tasks. */
    private static final String SQL_DELETE_OLD_TASKS =
            "DELETE FROM " + TBL_TASK + " WHERE " + KEY_TASK_RETRY_UTC_DATETIME + "<?";

    /** Remove old events. */
    private static final String SQL_DELETE_OLD_EVENTS =
            "DELETE FROM " + TBL_EVENT + " WHERE " + KEY_EVENT_UTC_DATETIME + "<?";

    @NonNull
    private final QueueDBHelper mQueueDBHelper;
    /** List of statements build by this adapter so that they can be removed on close. */
    private final Collection<SQLiteStatement> mStatements = new ArrayList<>();
    private SQLiteStatement mCheckTaskExistsStmt;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    QueueDAO(@NonNull final Context context) {
        mQueueDBHelper = new QueueDBHelper(context);
    }

    /**
     * Get a database connection.
     *
     * @return The database
     */
    @NonNull
    private SQLiteDatabase getDb() {
        return mQueueDBHelper.getWritableDatabase();
    }

    /**
     * Create the specified queue if it does not exist.
     *
     * @param name Name of the queue
     */
    void createQueue(@NonNull final String name) {
        final long id = getQueueId(name);
        if (id == 0) {
            final ContentValues cv = new ContentValues();
            cv.put(KEY_QUEUE_NAME, name);
            getDb().insert(TBL_QUEUE, null, cv);
        }
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
    void initAllQueues(@NonNull final QueueManager queueManager) {
        try (Cursor cursor = getDb().rawQuery(SQL_FETCH_ALL_QUEUES, null)) {
            while (cursor.moveToNext()) {
                new Queue(queueManager, cursor.getString(0)).start();
            }
        }
    }

    /**
     * Enqueue a task to be run in the specified queue.
     *
     * @param task      Task instance to save and run
     * @param queueName Queue name
     */
    void enqueueTask(@NonNull final TQTask task,
                     @NonNull final String queueName) {
        final long queueId = SanityCheck.requireValue(getQueueId(queueName), queueName);

        final ContentValues cv = new ContentValues();
        cv.put(KEY_TASK, SerializationUtils.serializeObject(task));
        cv.put(KEY_TASK_CATEGORY, task.getCategory());
        cv.put(KEY_QUEUE_ID, queueId);
        final long id = getDb().insert(TBL_TASK, null, cv);
        task.setId(id);
    }

    /**
     * Return a ScheduledTask object for the next task that should be run from the passed
     * queue. Return {@code null} if no more entries.
     * <p>
     * This method will find the highest priority RUNNABLE task, and failing that the
     * next available task.
     *
     * @param context   Current context
     * @param queueName Name of queue to check
     *
     * @return ScheduledTask object containing details of task or {@code null} for none.
     */
    @Nullable
    ScheduledTask getNextTask(@NonNull final Context context,
                              @NonNull final String queueName) {

        final LocalDateTime currentUtcDateTime = LocalDateTime.now(ZoneOffset.UTC);
        final String[] sqlArg = new String[]{
                queueName, currentUtcDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)};

        final SQLiteDatabase db = getDb();
        Cursor cursor = null;

        try {
            // Get highest priority task that can run now
            cursor = db.rawQuery(SQL_TASK_WHICH_CAN_RUN_NOW, sqlArg);
            // If there is no task available now. Look for one that is waiting.
            if (!cursor.moveToFirst()) {
                // Close before reusing
                cursor.close();
                cursor = db.rawQuery(SQL_TASK_WHICH_IS_WAITING, sqlArg);
            }

            // Still no tasks to run ? All done.
            if (!cursor.moveToFirst()) {
                return null;
            }

            // Determine the number of milliseconds to wait before we should run the task
            LocalDateTime utcRetryDate = DateParser.getInstance(context).parseISO(
                    cursor.getString(cursor.getColumnIndex(KEY_TASK_RETRY_UTC_DATETIME)));
            if (utcRetryDate == null) {
                utcRetryDate = LocalDateTime.now(ZoneOffset.UTC);
            }

            final long millisUntilRunnable;
            if (utcRetryDate.isAfter(currentUtcDateTime)) {
                // set timeUntilRunnable to let caller know the queue is not empty
                millisUntilRunnable = Duration.between(currentUtcDateTime, utcRetryDate).abs()
                                              .toMillis();
            } else {
                // No need to wait
                millisUntilRunnable = 0;
            }

            final int id = cursor.getInt(cursor.getColumnIndex(KEY_PK_ID));
            final byte[] serializedTask = cursor.getBlob(cursor.getColumnIndex(KEY_TASK));

            final int retries = cursor.getInt(cursor.getColumnIndex(KEY_TASK_RETRY_COUNT));

            return new ScheduledTask(id, serializedTask, retries, millisUntilRunnable);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Save the passed task back to the database. The parameter must be a Task that
     * is already in the database. This method is used to preserve a task state.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task The task to be updated
     */
    void updateTask(@NonNull final TQTask task) {
        final ContentValues cv = new ContentValues();
        cv.put(KEY_TASK, SerializationUtils.serializeObject(task));
        cv.put(KEY_TASK_CATEGORY, task.getCategory());
        getDb().update(TBL_TASK, cv, KEY_PK_ID + "=?",
                       new String[]{String.valueOf(task.getId())});
    }

    /**
     * Set the related task record as successfully completed.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param id The task to be updated
     */
    void setTaskCompleted(final long id) {
        if (hasEvents(id)) {
            // Just set is as completed
            final ContentValues cv = new ContentValues();
            cv.put(KEY_TASK_STATUS_CODE, TQTask.COMPLETED);
            getDb().update(TBL_TASK, cv, KEY_PK_ID + "=?", new String[]{String.valueOf(id)});
        } else {
            // Delete successful tasks which have no events
            getDb().delete(TBL_TASK, KEY_PK_ID + "=?", new String[]{String.valueOf(id)});
        }
    }

    /**
     * Save and set the task as failed.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task    The task to be updated
     * @param message Final message to store. Task can also contain an Exception object.
     */
    void setTaskFailed(@NonNull final TQTask task,
                       @NonNull final String message) {
        final ContentValues cv = new ContentValues();
        cv.put(KEY_TASK_FAILURE_REASON, message);
        cv.put(KEY_TASK_STATUS_CODE, TQTask.FAILED);
        cv.put(KEY_TASK, SerializationUtils.serializeObject(task));
        final Exception e = task.getLastException();
        if (e != null) {
            cv.put(KEY_TASK_EXCEPTION, SerializationUtils.serializeObject(e));
        }

        getDb().update(TBL_TASK, cv, KEY_PK_ID + "=?",
                       new String[]{String.valueOf(task.getId())});
    }

    /**
     * Save and requeue the passed task.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param task The task to requeue
     */
    void requeueTask(@NonNull final TQTask task) {
        if (!task.canRetry()) {
            setTaskFailed(task, "Retry limit exceeded");
        } else {
            // Compute time Task can next be run
            final String utcRetryDateTime =
                    LocalDateTime.now(ZoneOffset.UTC)
                                 .plusSeconds(task.getRetryDelay())
                                 .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            final ContentValues cv = new ContentValues();
            cv.put(KEY_TASK_RETRY_UTC_DATETIME, utcRetryDateTime);
            cv.put(KEY_TASK_RETRY_COUNT, task.getRetries() + 1);
            cv.put(KEY_TASK, SerializationUtils.serializeObject(task));
            getDb().update(TBL_TASK, cv, KEY_PK_ID + "=?",
                           new String[]{String.valueOf(task.getId())});
        }
    }

    /**
     * Store an Event object for later retrieval after task has completed. This is
     * analogous to writing a line to the 'log file' for the task.
     * <p>
     * <strong>Note:</strong> this code must not assume the task exists.
     * IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
     *
     * @param taskId The task to be updated
     * @param event  to store
     */
    void storeTaskEvent(final long taskId,
                        @SuppressWarnings("TypeMayBeWeakened") @NonNull final TQEvent event) {
        final SQLiteDatabase db = getDb();

        // Construct statements we want
        if (mCheckTaskExistsStmt == null) {
            final String sql = "SELECT COUNT(*) FROM " + TBL_TASK + " WHERE " + KEY_PK_ID + "=?";
            mCheckTaskExistsStmt = db.compileStatement(sql);
            mStatements.add(mCheckTaskExistsStmt);
        }

        db.beginTransaction();
        try {
            // Check task exists
            mCheckTaskExistsStmt.bindLong(1, taskId);
            final long count = mCheckTaskExistsStmt.simpleQueryForLong();
            if (count > 0) {
                // Setup parameters for insert
                final ContentValues cv = new ContentValues();
                cv.put(KEY_TASK_ID, taskId);
                cv.put(KEY_EVENT, SerializationUtils.serializeObject(event));

                final long eventId = db.insert(TBL_EVENT, null, cv);
                db.setTransactionSuccessful();
                event.setId(eventId);
            }
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Get a Cursor returning all events for the passed task.
     *
     * @param taskId id of the task whose exceptions we want
     *
     * @return Cursor
     */
    @NonNull
    Cursor getEvents(final long taskId) {
        return getDb().rawQuery(SQL_FETCH_EVENTS_FOR_TASK, new String[]{String.valueOf(taskId)});
    }

    /**
     * Get a Cursor returning all events.
     *
     * @return Cursor
     */
    @NonNull
    Cursor getEvents() {
        return getDb().rawQuery(SQL_FETCH_ALL_EVENTS, null);
    }

    /**
     * Get a Cursor returning all tasks.
     *
     * @return Cursor
     */
    @NonNull
    Cursor getTasks() {
        return getDb().rawQuery(SQL_FETCH_ALL_TASKS, null);
    }

    /**
     * Check if there are active tasks of the specified category.
     *
     * @param category Category to get
     *
     * @return {@code true} if there are active tasks
     */
    boolean hasActiveTasks(final long category) {
        try (SQLiteStatement stmt = getDb().compileStatement(SQL_COUNT_ACTIVE_TASKS)) {
            stmt.bindLong(1, category);
            return stmt.simpleQueryForLong() > 0;
        } catch (@NonNull final SQLiteDoneException ignore) {
            return false;
        }
    }

    /**
     * Check if the task has any Events recorded.
     *
     * @param taskId to check
     *
     * @return {@code true} if it has events
     */
    private boolean hasEvents(final long taskId) {
        try (SQLiteStatement stmt = getDb().compileStatement(SQL_COUNT_EVENTS)) {
            stmt.bindLong(1, taskId);
            return stmt.simpleQueryForLong() > 0;
        } catch (@NonNull final SQLiteDoneException ignore) {
            return false;
        }
    }

    /**
     * Delete the specified Event object.
     *
     * @param id of Event to delete.
     */
    void deleteEvent(final long id) {
        final SQLiteDatabase db = getDb();
        db.beginTransaction();
        try {
            db.delete(TBL_EVENT, KEY_PK_ID + "=?", new String[]{String.valueOf(id)});
            cleanupOrphans(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Delete the specified Task object.
     *
     * @param id of Task to delete.
     */
    void deleteTask(final long id) {
        final SQLiteDatabase db = getDb();
        db.beginTransaction();
        try {
            db.delete(TBL_EVENT, KEY_TASK_ID + "=?", new String[]{String.valueOf(id)});
            db.delete(TBL_TASK, KEY_PK_ID + "=?", new String[]{String.valueOf(id)});
            cleanupOrphans(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void deleteTasksOlderThan(final int days) {
        final String utcDateTime = LocalDateTime.now(ZoneOffset.UTC)
                                                .minusDays(days)
                                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        final SQLiteDatabase db = getDb();

        db.beginTransaction();
        try {
            try (SQLiteStatement stmt = db.compileStatement(SQL_DELETE_OLD_EVENTS_WITH_TASKS)) {
                stmt.bindString(1, utcDateTime);
                stmt.executeUpdateDelete();
            }

            try (SQLiteStatement stmt = db.compileStatement(SQL_DELETE_OLD_TASKS)) {
                stmt.bindString(1, utcDateTime);
                stmt.executeUpdateDelete();
            }

            cleanupOrphans(db);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void deleteEventsOlderThan(final int days) {
        final String utcDateTime = LocalDateTime.now(ZoneOffset.UTC)
                                                .minusDays(days)
                                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        final SQLiteDatabase db = getDb();

        db.beginTransaction();
        try {
            try (SQLiteStatement stmt = db.compileStatement(SQL_DELETE_OLD_EVENTS)) {
                stmt.bindString(1, utcDateTime);
                stmt.executeUpdateDelete();
            }

            cleanupOrphans(db);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Delete orphaned events and tasks.
     *
     * <strong>Transaction:</strong> required
     *
     * @param db with a TRANSACTION started.
     */
    private void cleanupOrphans(@NonNull final SQLiteDatabase db) {
        if (!db.inTransaction()) {
            throw new TransactionException(TransactionException.REQUIRED);
        }
        try (SQLiteStatement stmt = db.compileStatement(SQL_DELETE_ORPHANED_EVENTS)) {
            stmt.executeUpdateDelete();
        }

        try (SQLiteStatement stmt = db.compileStatement(SQL_DELETE_COMPLETED_TASKS)) {
            stmt.executeUpdateDelete();
        }
    }

    /**
     * Generic function to close the database.
     */
    @Override
    public void close() {
        try {
            for (final SQLiteStatement s : mStatements) {
                try {
                    s.close();
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore
                }
            }
            mQueueDBHelper.close();
        } catch (@NonNull final RuntimeException ignore) {
            // ignore
        } finally {
            mStatements.clear();
        }
    }

    /**
     * Class containing information about the next task to be executed in a given queue.
     */
    static class ScheduledTask {

        /** Time, in milliseconds, until Task needs to be executed. */
        private final long mMillisUntilRunnable;
        /** Blob for Task retrieved from DB. We do not deserializeObject until necessary. */
        private final byte[] mBlob;
        /** Retry count retrieved from DB. */
        private final int mRetries;
        /** id of Task. */
        private final int mId;

        /**
         * Constructor.
         *
         * @param id                  new task id to apply
         * @param serializedTask      the task
         * @param retries             number of retries left
         * @param millisUntilRunnable Milliseconds until task should be run
         */
        ScheduledTask(final int id,
                      @NonNull final byte[] serializedTask,
                      final int retries,
                      final long millisUntilRunnable) {
            mMillisUntilRunnable = millisUntilRunnable;
            mId = id;
            mRetries = retries;
            mBlob = serializedTask;
        }

        /**
         * Return the number of Milliseconds until task should be run.
         *
         * @return Milliseconds to wait
         */
        long getMillisUntilRunnable() {
            return mMillisUntilRunnable;
        }

        /**
         * Return the Task object after de-serialising the blob.
         *
         * @return related Task object
         */
        @Nullable
        TQTask getTask() {
            final TQTask task;

            try {
                task = SerializationUtils.deserializeObject(mBlob);
            } catch (@NonNull final SerializationUtils.DeserializationException e) {
                return null;
            }

            task.setId(mId);
            task.setRetries(mRetries);
            // Initialise this here so that it can be adjusted by the task when it is run.
            task.setRetryDelay();

            return task;
        }
    }
}
