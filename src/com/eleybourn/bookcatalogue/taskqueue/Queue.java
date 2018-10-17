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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.taskqueue.DBAdapter.ScheduledTask;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.TaskActions;
import com.eleybourn.bookcatalogue.taskqueue.Task.TaskState;

import java.lang.ref.WeakReference;

/**
 * Represents a thread that runs tasks from a related named queue.
 *
 * @author Philip Warner
 */
public class Queue extends Thread {
    /** Application context. Needed for DB access */
    private final Context mApplicationContext;
    /** QueueManager that owns this Queue object */
    @NonNull
    private final QueueManager mManager;
    /** Name of this Queue */
    @NonNull
    private final String mName;
    /** DBAdapter used internally */
    private DBAdapter mDb;

    /** Currently running task */
    private WeakReference<Task> mTask = null;

    /** Options to indicate process is terminating */
    private boolean mTerminate = false;

    /**
     * Constructor. Nothing to see here, move along. Just save the properties and start the thread.
     *
     * @author Philip Warner
     */
    public Queue(@NonNull final Context context, @NonNull final QueueManager manager, @NonNull final String queueName) {
        mApplicationContext = context.getApplicationContext();
        mName = queueName;
        mManager = manager;
        // Set the thread name to something helpful. This is distinct from the Queue name.
        this.setName("Queue " + queueName);

        // Add this object to the active queues list in the manager. It is important
        // that this is done in the constructor AND that new queues are created inside
        // code synchronized on the manager.
        mManager.queueStarting(this);

        start();
    }

    /**
     * Return the bare queue name, as opposed to the thread name
     */
    @NonNull
    String getQueueName() {
        return mName;
    }

    /**
     * Terminate processing.
     */
    public void finish() {
        mTerminate = true;
        this.interrupt();
    }

    /**
     * Main worker thread logic
     */
    public void run() {
        try {
            // Get a database adapter
            mDb = new DBAdapter(mApplicationContext);
            // Run until we're told not to or until we decide no to.
            while (!mTerminate) {
                ScheduledTask scheduledTask;
                Task task;
                // All queue manipulation needs to be synchronized on the manager, as does
                // assignments of 'active' tasks in queues.
                synchronized (mManager) {
                    scheduledTask = mDb.getNextTask(mName);
                    if (scheduledTask == null) {
                        // No more tasks. Remove from manager and terminate.
                        mTerminate = true;
                        mManager.queueTerminating(this);
                        return;
                    }
                    if (scheduledTask.timeUntilRunnable == 0) {
                        // Ready to run now.
                        task = scheduledTask.getTask();
                        mTask = new WeakReference<>(task);
                    } else {
                        mTask = null;
                        task = null;
                    }
                }

                // If we get here, we have a task, or know that there is one waiting to run. Just wait.
                // ENHANCE: A future optimization might be to put an Alarm in the QueueManager
                // for any wait that is longer than a minute.
                if (task != null) {
                    handleTask(task);
                } else {
                    // Not ready, just wait. Allow for possible wake-up calls if something else gets queued.
                    synchronized (this) {
                        this.wait(scheduledTask.timeUntilRunnable);
                    }
                }
            }
        } catch (Exception e) {
            Logger.error(e);
        } finally {
            try {
                // Close the DB. SQLite will complain otherwise.
                if (mDb != null) {
                    mDb.getDb().close();
                }
                // Just in case (the queue manager does check the queue before doing the delete).
                synchronized (mManager) {
                    mManager.queueTerminating(this);
                }
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Run the task then save the results.
     */
    private void handleTask(@NonNull final Task task) {
        boolean result = false;
        boolean requeue = false;
        try {
            task.setException(null);
            task.setState(TaskState.running);
            mManager.notifyTaskChange(task, TaskActions.running);
            result = mManager.runOneTask(task);
            requeue = !result;
        } catch (Exception e) {
            // Don't overwrite exception set by handler
            if (task.getException() == null) {
                task.setException(e);
            }
            Logger.error(e,"Error running task " + task.getId());
        }
        handleResult(task, result, requeue);
    }

    /**
     * Update the related database record to process the task correctly.
     *
     * @param task    Task object
     * @param result  true on success, false on failure
     * @param requeue true if requeue needed
     */
    private void handleResult(@NonNull final Task task, final boolean result, final boolean requeue) {
        TaskActions message;
        synchronized (mManager) {
            if (task.isAborting()) {
                mDb.deleteTask(task.getId());
                message = TaskActions.completed;
            } else if (result) {
                mDb.setTaskOk(task);
                message = TaskActions.completed;
            } else if (requeue) {
                mDb.setTaskRequeue(task);
                message = TaskActions.waiting;
            } else {
                Exception e =  task.getException();
                String msg = null;
                if (e != null) {
                   msg = e.getLocalizedMessage();
                }
                mDb.setTaskFail(task, "Unhandled exception while running task: " + msg);
                message = TaskActions.completed;
            }
            mTask.clear();
            mTask = null;
        }
        mManager.notifyTaskChange(task, message);
    }

    @Nullable
    public Task getTask() {
        if (mTask == null) {
            return null;
        } else {
            return mTask.get();
        }
    }

}
