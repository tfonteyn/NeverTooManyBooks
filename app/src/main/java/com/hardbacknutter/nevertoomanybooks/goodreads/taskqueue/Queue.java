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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueDAO.ScheduledTask;

/**
 * Represents a thread that runs tasks from a related named queue.
 */
class Queue
        extends Thread {

    private static final String TAG = "Queue";

    /** QueueManager that owns this Queue object. */
    @NonNull
    private final QueueManager mManager;
    /** Name of this Queue. */
    @NonNull
    private final String mName;
    /** TaskQueueDAO used internally. */
    private TaskQueueDAO mTaskQueueDAO;

    /** Currently running task. */
    private WeakReference<Task> mTask;

    /** Flag to indicate process is terminating. */
    private boolean mTerminate;

    /**
     * Constructor. Nothing to see here, move along. Just save the properties and start the thread.
     */
    Queue(@NonNull final QueueManager manager,
          @NonNull final String queueName) {

        mName = queueName;
        mManager = manager;
        // Set the thread name to something helpful. This is distinct from the Queue name.
        setName("Queue " + queueName);

        // Add this object to the active queues list in the manager. It is important
        // that this is done in the constructor AND that new queues are created inside
        // code synchronized on the manager.
        mManager.onQueueStarting(this);

        start();
    }

    /**
     * @return the bare queue name, as opposed to the thread name.
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
        interrupt();
    }

    /**
     * Main worker thread logic.
     */
    public void run() {
        try {
            mTaskQueueDAO = new TaskQueueDAO();
            while (!mTerminate) {
                ScheduledTask scheduledTask;
                Task task;
                // All queue manipulation needs to be synchronized on the manager, as does
                // assignments of 'active' tasks in queues.
                synchronized (mManager) {
                    scheduledTask = mTaskQueueDAO.getNextTask(mName);
                    if (scheduledTask == null) {
                        // No more tasks. Remove from manager and terminate.
                        mTerminate = true;
                        mManager.onQueueTerminating(this);
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

                // If we get here, we have a task, or know that there is one waiting to run.
                // Just wait for any wait that is longer than a minute.
                if (task != null) {
                    runTask(task);
                } else {
                    // Not ready, just wait. Allow for possible wake-up calls if something
                    // else gets queued.
                    synchronized (this) {
                        wait(scheduledTask.timeUntilRunnable);
                    }
                }
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
            Logger.error(TAG, e);
        } finally {
            try {
                if (mTaskQueueDAO != null) {
                    mTaskQueueDAO.getDb().close();
                }
                // Just in case (the queue manager does check the queue before doing the delete).
                synchronized (mManager) {
                    mManager.onQueueTerminating(this);
                }
            } catch (@NonNull final RuntimeException ignore) {
            }
        }
    }

    /**
     * Run the task then save the results.
     */
    private void runTask(@NonNull final Task task) {
        boolean result = false;
        boolean requeue = false;
        try {
            task.setException(null);
            // notify here, as we allow mManager.runTask to be overridden
            mManager.notifyTaskChange();
            result = mManager.runTask(task);
            requeue = !result;
        } catch (@NonNull final RuntimeException e) {
            // Don't overwrite exception set by handler
            if (task.getException() == null) {
                task.setException(e);
            }
            Logger.error(TAG, e, "Error running task " + task.getId());
        }
        handleTaskResult(task, result, requeue);
    }

    /**
     * Update the related database record to process the task correctly.
     *
     * @param task    Task object
     * @param result  {@code true} on Save, {@code false} on cancel
     * @param requeue {@code true} if requeue needed
     */
    private void handleTaskResult(@NonNull final Task task,
                                  final boolean result,
                                  final boolean requeue) {
        synchronized (mManager) {

            if (task.isAborting()) {
                mTaskQueueDAO.deleteTask(task.getId());
            } else if (result) {
                mTaskQueueDAO.setTaskOk(task);
            } else if (requeue) {
                mTaskQueueDAO.setTaskRequeue(task);
            } else {
                Exception e = task.getException();
                String msg = null;
                if (e != null) {
                    msg = e.getLocalizedMessage();
                }
                mTaskQueueDAO.setTaskFail(task, "Unhandled exception while running task: " + msg);
            }
            mTask.clear();
            mTask = null;
        }
        mManager.notifyTaskChange();
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
