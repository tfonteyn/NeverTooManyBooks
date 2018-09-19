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

import com.eleybourn.bookcatalogue.utils.SerializationUtils;

import com.eleybourn.bookcatalogue.taskqueue.Task.TaskState;
import com.eleybourn.bookcatalogue.taskqueue.TasksCursor.TaskCursorSubtype;

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
 * Database later. Implements all direct database access.
 * 
 * @author Philip Warner
 */
public class DbAdapter {
	private final DbHelper m_dbHelper;
	private final Context m_appContext;

	private static final String[] EMPTY_STRING_ARRAY = new String[] {};

	/** List of statements build by this adapter so that they can be removed on close */
	private final ArrayList<SQLiteStatement> m_statements = new ArrayList<>();

	/**
	 * Constructor
	 */
	DbAdapter(Context context) {
		m_appContext = context.getApplicationContext();
		m_dbHelper = new DbHelper(m_appContext);
	}

	/**
	 * Get a database connection.
	 * 
	 * @return The database
	 */
	protected SQLiteDatabase getDb() {
		return m_dbHelper.getWritableDatabase();
	}

	/**
	 * Lookup the ID of a queue based on the name.
	 * 
	 * @param name	Queue Name
	 * @return		The ID of the queue, 0 if no match
	 */
	private long getQueueId(String name) {
		final String sql = "select " + DOM_ID + " from " + TBL_QUEUE + " Where " + DOM_NAME + " = ?";
		SQLiteDatabase db = getDb();

		Cursor c = db.rawQuery(sql, new String[] {name});
		try {
			if (c.moveToFirst()) {
				return c.getInt(0);
			} else {
				return 0;
			}
		} finally {
			c.close();
		}
	}
	
	/**
	 * Retrieve all queues and instantiate them for the passed QueueManager.
	 * ENHANCE: Change this to only return queues with jobs of status 'Q'
	 * 			So that queues will not be started if no jobs. Problems arise
	 * 			working out how to handle 'waiting' jobs (if we want to allow
	 * 			for a 'waiting' status -- eg. waiting for network).
	 * 
	 * @param manager	Owner of the created Queue objects
	 */
	protected void getAllQueues(QueueManager manager) {
		String sql = "select " + DOM_NAME + " from " + TBL_QUEUE + " Order by " + DOM_NAME;
		SQLiteDatabase db = getDb();

		Cursor c = db.rawQuery(sql, EMPTY_STRING_ARRAY);
		try {
			while (c.moveToNext()) {
				String name = c.getString(0);
				// Create the Queue. It will register itself with its QueueManager.
				new Queue(m_appContext, manager, name);
			}	
		} finally {
			c.close();
		}
		return;
	}
	
	/**
	 * Class containing information about the next task to be executed in a given queue.
	 * 
	 * @author Philip Warner
	 */
	protected class ScheduledTask {
		/** Time, in milliseconds, until Task needs to be executed. */
		long timeUntilRunnable;
		/** Blob for TAsk retrieved from DB. We do not deserialize until necessary. */
		byte[] m_blob;
		/** Retry count retrieved from DB. */
		int m_retries;
		/** ID of Task. */
		int id;

		/**
		 * Return the Task object after Deserializing the blob.
		 * 
		 * @return	related Task object
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

		/** 
		 * Constructor
		 * 
		 * @param timeUntilRunnable	Milliseconds until task should be run
		 * @param c	Cursor positioned at task details
		 */
		ScheduledTask(long timeUntilRunnable, Cursor c) {
			this.timeUntilRunnable = timeUntilRunnable;
			int taskCol = c.getColumnIndex(DOM_TASK);
		    m_retries = c.getInt(c.getColumnIndex(DOM_RETRY_COUNT));
		    this.id = c.getInt(c.getColumnIndex(DOM_ID));
			m_blob = c.getBlob(taskCol);
		}

	}

	/**
	 * Return a ScheduledTask object for the next task that should be run from the passed 
	 * queue. Return NULL if no more entries.
	 * 
	 * This method will find the highest priority RUNNABLE task, and failing that the 
	 * next available task.
	 * 
	 * @param queueName	Name of queue to check
	 * @return ScheduledTask object containing details of task
	 */
	protected ScheduledTask getNextTask(String queueName) {
		// Get current time
		Date currTime = new Date();
		long timeToNext;

		String currTimeStr = Utils.date2string(currTime);
		SQLiteDatabase db = getDb();

		String baseSql = "Select j.* from " + TBL_QUEUE + " q"
				+ " join " + TBL_TASK + " j on j." + DOM_QUEUE_ID + " = q." + DOM_ID
				+ " where "
				+ "  j." + DOM_STATUS_CODE + "= 'Q'"
				+ "  and q." + DOM_NAME + " = ?";

		// Query to check for any task that CAN run now, sorted by priority then date/id
		String canRunSql = baseSql
				+ "  and j." + DOM_RETRY_DATE + " <= ?"
				+ "  Order by " + DOM_PRIORITY + " asc, " + DOM_RETRY_DATE + " asc," + DOM_ID + " asc"
				+ " Limit 1";

		// Get next task that CAN RUN NOW
		Cursor c = db.rawQuery(canRunSql, new String[] {queueName, currTimeStr});
		if (!c.moveToFirst()) {
			// Close this cursor.
			c.close();
			// There is no task available now. Look for one that is waiting.
			String sql = baseSql 
					+ "  and j." + DOM_RETRY_DATE + " > ?"
					+ "  Order by " + DOM_RETRY_DATE + " asc, " + DOM_PRIORITY + " asc, " + DOM_ID + " asc"
					+ " Limit 1";
			c = db.rawQuery(sql, new String[] {queueName, currTimeStr});
		}

		try {
			// If no matching row, return NULL
			if (!c.moveToFirst())
				return null;

			// Find task details and create ScheduledTask object
			int dateCol = c.getColumnIndex(DOM_RETRY_DATE);
			Date retryDate = Utils.string2date(c.getString(dateCol));

			if (retryDate.after(currTime)) {
				// set timeToNext to let called know queue is not empty
				timeToNext = (retryDate.getTime() - currTime.getTime());
			} else {
				// No need to wait
				timeToNext = 0;
			}

			return new ScheduledTask(timeToNext, c);
			
		} finally {
			if (c != null)
				c.close();
		}
	}

	/**
	 * Create the specified queue if it does not exist.
	 * 
	 * @param queueName    Name of the queue
	 * @return			ID of resulting queue
	 */
	protected void createQueue(String queueName) {
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
	protected void updateTask(Task task) {
		ContentValues cv = new ContentValues();
		cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
		cv.put(DOM_CATEGORY, task.getCategory());
		SQLiteDatabase db = getDb();
		db.update(TBL_TASK, cv, DOM_ID + " = " + task.getId(), EMPTY_STRING_ARRAY);
	}

	/**
	 * Enqueue a task to be run in the specified queue.
	 * 
	 * @param task        Task instance to save and run
	 * @param queueName    Queue name
	 * @return			The ID of the resulting row.
	 */
	protected void enqueTask(Task task, String queueName) {
		long queueId = getQueueId(queueName);
		if (queueId == 0) 
			throw new RuntimeException("Queue '" + queueName + "' does not exist; unable to queue request");

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
	 * @param task	Task object
	 */
	protected void setTaskOk(Task task) {
		SQLiteDatabase db = getDb();
		String sql;

		// See if the task has any Events recorded
		sql = "Select count(*) from " + TBL_EVENT + " where " + DOM_TASK_ID + " = " + task.getId();
		Cursor c = db.rawQuery(sql, EMPTY_STRING_ARRAY);
		try {
			if (c.moveToFirst() && c.getLong(0) == 0) {
				// Delete successful tasks with no events
				db.delete(TBL_TASK, DOM_ID + " = " + task.getId(), EMPTY_STRING_ARRAY);
			} else {
				// Just mark is as successful
				sql = "Update " + TBL_TASK + " set " + DOM_STATUS_CODE + "= 'S' where " + DOM_ID + " = " + task.getId();
				getDb().execSQL(sql);			
			}
		} finally {
			c.close();
		}
	}

	protected void cleanupOldTasks(int ageInDays) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -ageInDays);
		String oneWeekAgo = Utils.date2string(cal.getTime());
		SQLiteDatabase db = getDb();
		String sql;

		db.beginTransaction();
		try {
			// Remove Events attached to old tasks
			sql = DOM_TASK_ID + " In ("
					+ "Select t." + DOM_ID + " from " + TBL_TASK + " t "
					+ " Where t." + DOM_RETRY_DATE + " < '" + oneWeekAgo + "')";
			db.delete(TBL_EVENT, sql, EMPTY_STRING_ARRAY);

			// Remove old Tasks
			sql = DOM_RETRY_DATE + " < '" + oneWeekAgo + "'";
			db.delete(TBL_TASK, sql, EMPTY_STRING_ARRAY);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		cleanupOrphans();
	}

	protected void cleanupOldEvents(int ageInDays) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -ageInDays);
		String oneWeekAgo = Utils.date2string(cal.getTime());
		SQLiteDatabase db = getDb();


		db.beginTransaction();
		try {
			db.delete(TBL_EVENT, DOM_EVENT_DATE + " < '" + oneWeekAgo + "'", EMPTY_STRING_ARRAY);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		cleanupOrphans();
	}

	protected void cleanupOrphans() {
		SQLiteDatabase db = getDb();
		db.beginTransaction();
		String sql;
		try {
			// Remove orphaned events -- should never be needed
			sql = "Not " + DOM_TASK_ID + " is NULL"
					+ " And Not Exists(Select * From " + TBL_TASK + " t Where " + TBL_EVENT + "." + DOM_TASK_ID + " = t." + DOM_ID + ")" ;
			db.delete(TBL_EVENT, sql, EMPTY_STRING_ARRAY);

			// Remove orphaned tasks THAT WERE SUCCESSFUL
			sql = "Not Exists(Select * From " + TBL_EVENT + " e Where e." + DOM_TASK_ID + " = " + TBL_TASK + "." + DOM_ID + ")"
					+ " and " + DOM_STATUS_CODE + " = 'S'";					
			db.delete(TBL_TASK, sql, EMPTY_STRING_ARRAY);
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
	 * @param task	task object to requeue.
	 */
	protected void setTaskRequeque(Task task) {
		int waitSecs;
		if (!task.canRetry()) {
			// We have waited a lot already; just give up.
			setTaskFail(task,"Retry limit exceeded");
		} else {
			task.setState(TaskState.waiting);
			// Compute time Task can next be run
			Calendar cal = Calendar.getInstance();
			waitSecs = task.getRetryDelay();			
			cal.add(Calendar.SECOND, waitSecs);
			// Convert to String
			String retryDate = Utils.date2string(cal.getTime());
			// Update record
			ContentValues cv = new ContentValues();
			cv.put(DOM_RETRY_DATE, retryDate);
			cv.put(DOM_RETRY_COUNT, task.getRetries()+1);
			cv.put(DOM_TASK, SerializationUtils.serializeObject(task));
			getDb().update(TBL_TASK, cv, DOM_ID + " = " + task.getId(), EMPTY_STRING_ARRAY);
		}
	}

	/**
	 * Save and mark the task as failed.
	 * 
	 * NOTE: this code must not assume the task exists. IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
	 * 
	 * @param task		Task that failed.
	 * @param message	Final message to store. Task can also contain an Exception object.
	 */
	protected void setTaskFail(Task task, String message) {
		task.setState(TaskState.failed);

		ContentValues cv = new ContentValues();
		cv.put(DOM_FAILURE_REASON, message);
		cv.put(DOM_STATUS_CODE, "F");
		cv.put(DOM_EXCEPTION, SerializationUtils.serializeObject(task.getException()));
		cv.put(DOM_TASK, SerializationUtils.serializeObject(task));

		getDb().update(TBL_TASK, cv, DOM_ID + " = " + task.getId(), EMPTY_STRING_ARRAY);
	}

	/**
	 * Store an Event object for later retrieval after task has completed. This is 
	 * analogous to writing a line to the 'log file' for the task.
	 * 
	 * NOTE: this code must not assume the task exists. IT MAY HAVE BEEN DELETED BY THE QUEUE MANAGER.
	 * 
	 * @param t		Related task
	 * @param e		Event (usually subclassed)
	 */
	private SQLiteStatement m_checkTaskExistsStmt = null;
	protected long storeTaskEvent(Task t, Event e) {
		SQLiteDatabase db = getDb();

		// Setup parameters for insert
		ContentValues cv = new ContentValues();
		cv.put(DOM_TASK_ID,  t.getId());
		cv.put(DOM_EVENT, SerializationUtils.serializeObject(e));

		// Construct statements we want
		if (m_checkTaskExistsStmt == null) {
			String sql = "Select Count(*) From " + TBL_TASK + " Where " + DOM_ID + " = ?";
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
				return eventId;
			} else {
				return 0;
			}
		} finally {
			db.endTransaction();
		}			
	}

	/** Static Factory object to create the custom cursor */
	private final CursorFactory m_EventsCursorFactory = new CursorFactory() {
			@Override
			public Cursor newCursor(
					SQLiteDatabase db,
					SQLiteCursorDriver masterQuery, 
					String editTable,
					SQLiteQuery query) 
			{
				return new EventsCursor(masterQuery, editTable, query);
			}
	};


	/**
	 * Static method to get an Events Cursor returning all events for the passed task.
	 * 
	 * @param db		Database
	 * @param taskId	ID of the task whose exceptions we want
	 * 
	 * @return			A new TaskExceptionsCursor
	 */
	private EventsCursor fetchTaskEvents(SQLiteDatabase db, long taskId) {
		String m_taskEventsQuery = "Select e.* From " + TBL_EVENT + " e "
				+ " Where e." + DOM_TASK_ID + " = ? "
				+ " Order by e." + DOM_ID + " asc";
		return (EventsCursor) db.rawQueryWithFactory(m_EventsCursorFactory, m_taskEventsQuery, new String[] {taskId+""}, "");
	}

	/**
	 * Static method to get an Events Cursor returning all events
	 *
	 * @return			A new TaskExceptionsCursor
	 */
	private EventsCursor fetchAllEvents(SQLiteDatabase db) {
		String m_eventsQuery = "Select * From " + TBL_EVENT + " Order by " + DOM_ID + " asc";
		return (EventsCursor) db.rawQueryWithFactory(m_EventsCursorFactory, m_eventsQuery, new String[] {}, "");
	}

	/**
	 * Return an EventsCursor for all events related to the specified task ID.
	 * 
	 * @param taskId	ID of the task
	 * 
	 * @return			Cursor of exceptions
	 */
	protected EventsCursor getTaskEvents(long taskId) {
		return fetchTaskEvents(getDb(), taskId);
	}

	/**
	 * Return an EventsCursor for all events.
	 *
	 * @return			Cursor of exceptions
	 */
	protected EventsCursor getAllEvents() {
		return fetchAllEvents(getDb());
	}

	/**
	 * Return as TasksCursor for the specified type.
	 * 
	 * @param type		Subtype of cursor to retrieve
	 * 
	 * @return			Cursor of exceptions
	 */
	protected TasksCursor getTasks(TaskCursorSubtype type) {
		return TasksCursor.fetchTasks(getDb(), type);
	}

	/**
	 * Return as TasksCursor for the specified category and type.
	 * 
	 * @param category	Category to get
	 * @param type		Subtype of cursor to retrieve
	 * 
	 * @return			Cursor of exceptions
	 */
	protected TasksCursor getTasks(long category, TaskCursorSubtype type) {
		return TasksCursor.fetchTasks(getDb(), category, type);
	}

	/**
	 * Delete the specified Event object.
	 * 
	 * @param id	ID of Event to delete.
	 */
	protected void deleteEvent(long id) {
		//String sql = "Delete from " + TBL_TASK_EXCEPTIONS + " Where " + DOM_ID + " = ?";	
		//ContentValues cv = new ContentValues();
		//cv.put(DOM_EVENT_ID, id);
		getDb().delete(TBL_EVENT, DOM_ID + " = ?", new String[] {id+""});
		cleanupOrphans();
	}

	/**
	 * Delete the specified Task object.
	 * 
	 * @param id	ID of Task to delete.
	 */
	protected void deleteTask(long id) {
		SQLiteDatabase db = getDb();
		db.beginTransaction();
		try {
			String[] args = new String[] {Long.toString(id)};
			db.delete(TBL_EVENT, DOM_TASK_ID + " = ?", args);
			db.delete(TBL_TASK, DOM_ID + " = ?", new String[] {id+""});
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
			for(SQLiteStatement s : m_statements) {
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

}
