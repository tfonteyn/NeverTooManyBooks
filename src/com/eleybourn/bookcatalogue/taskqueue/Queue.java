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
import android.util.Log;

import com.eleybourn.bookcatalogue.taskqueue.DbAdapter.ScheduledTask;
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
    private final Context m_appContext;
    /** QueueManager that owns this Queue object */
    private final QueueManager m_manager;
    /** Name of this Queue */
    private final String m_name;
    /** DbAdapter used internally */
    private DbAdapter m_dba;

    /** Currently running task */
    private WeakReference<Task> m_task = null;

    /** Flag to indicate process is terminating */
    private boolean mTerminate = false;

    /**
     * Constructor. Nothing to see here, move along. Just save the properties
     * and start the thread.
     *
     * @author Philip Warner
     */
    public Queue(Context context, QueueManager manager, String queueName) {
        m_appContext = context.getApplicationContext();
        m_name = queueName;
        m_manager = manager;
        // Set the thread name to something helpful. This is distinct from the Queue name.
        this.setName("Queue " + queueName);

        // Add this object to the active queues list in the manager. It is important
        // that this is done in the constructor AND that new queues are created inside
        // code synchronized on the manager.
        m_manager.queueStarting(this);

        // Run the thread
        start();
    }

    /**
     * Return the bare queue name, as opposed to the thread name
     */
    String getQueueName() {
        return m_name;
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
        System.out.println("Queue " + m_name + " starting");
        try {
            // Get a database adapter
            m_dba = new DbAdapter(m_appContext);
            // Run until we're told not to or until we decide no to.
            while (!mTerminate) {
                ScheduledTask scheduledTask;
                Task task;
                // All queue manipulation needs to be synchronized on the manager, as does
                // assignments of 'active' tasks in queues.
                synchronized (m_manager) {
                    scheduledTask = m_dba.getNextTask(m_name);
                    if (scheduledTask == null) {
                        // No more tasks. Remove from manager and terminate.
                        mTerminate = true;
                        m_manager.queueTerminating(this);
                        return;
                    }
                    if (scheduledTask.timeUntilRunnable == 0) {
                        // Ready to run now.
                        System.out.println("Queue " + m_name + " running task " + scheduledTask.id);
                        task = scheduledTask.getTask();
                        m_task = new WeakReference<>(task);
                    } else {
                        m_task = null;
                        task = null;
                    }
                }

                // If we get here, we have a task, or know that there is one waiting to run. Just wait.
                // ENHANCE: A future optimization might be to put an Alarm in the QueueManager for any wait that
                // is longer than a minute.
                if (task != null) {
                    handleTask(task);
                } else {
                    // Not ready, just wait. Allow for possible wake-up calls if something else gets queued.
                    System.out.println("Queue " + m_name + " waiting " + scheduledTask.timeUntilRunnable + " for task " + scheduledTask.id);
                    synchronized (this) {
                        this.wait(scheduledTask.timeUntilRunnable);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("Queue", e.getMessage());
            //Logger.logError(e);
        } finally {
            try {
                // Close the DB. SQLite will complain otherwise.
                if (m_dba != null) {
                    m_dba.getDb().close();
                }
                // Just in case (the queue manager does check the queue before doing the delete).
                synchronized (m_manager) {
                    m_manager.queueTerminating(this);
                }
            } catch (Exception ignore) {
                // Ignore
            }
            System.out.println("Queue " + m_name + " terminating");
        }
    }

    /**
     * Run the task then save the results.
     */
    private void handleTask(final Task task) {
        boolean result = false;
        boolean requeue = false;
        try {
            task.setException(null);
            task.setState(TaskState.running);
            m_manager.notifyTaskChange(task, TaskActions.running);
            result = m_manager.runOneTask(task);
            requeue = !result;
        } catch (Exception e) {
            // Don't overwrite exception set by handler
            if (task.getException() == null) {
                task.setException(e);
            }
            Log.e("Error running task " + task.getId(), e.getMessage());
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
    private void handleResult(Task task, boolean result, boolean requeue) {
        TaskActions message;
        synchronized (m_manager) {
            if (task.isAborting()) {
                m_dba.deleteTask(task.getId());
                message = TaskActions.completed;
            } else if (result) {
                System.out.println("Task " + task.getId() + " succeeded");
                m_dba.setTaskOk(task);
                message = TaskActions.completed;
            } else if (requeue) {
                System.out.println("Task " + task.getId() + " requeueing");
                m_dba.setTaskRequeue(task);
                message = TaskActions.waiting;
            } else {
                System.out.println("Task " + task.getId() + " failed");
                m_dba.setTaskFail(task, "Unhandled exception while running task: " + task.getException().getMessage());
                message = TaskActions.completed;
            }
            m_task.clear();
            m_task = null;
        }
        m_manager.notifyTaskChange(task, message);
    }

    public Task getTask() {
        if (m_task == null) {
            return null;
        } else {
            return m_task.get();
        }
    }

}
