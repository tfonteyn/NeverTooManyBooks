/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TaskQueueDao.ScheduledTask;

/**
 * Represents a thread that runs tasks from a related named queue.
 */
class Queue
        extends Thread {

    /** Log tag. */
    private static final String TAG = "Queue";

    /** QueueManager that owns this Queue object. */
    @NonNull
    private final QueueManager mQueueManager;
    /** Name of this Queue. */
    @NonNull
    private final String mName;

    /** Currently running task. */
    @Nullable
    private WeakReference<TQTask> mCurrentTaskReference;

    /** Flag to indicate process is terminating. */
    private boolean mStopping;

    /**
     * Constructor.
     *
     * @param queueManager to connect to
     * @param queueName    to use
     */
    Queue(@NonNull final QueueManager queueManager,
          @NonNull final String queueName) {

        mName = queueName;
        mQueueManager = queueManager;
        // Set the thread name to something helpful. This is distinct from the Queue name.
        setName("Queue " + queueName);

        // Add this object to the active queues list in the manager. It is important
        // that this is done in the constructor AND that new queues are created inside
        // code synchronized on the manager.
        mQueueManager.onQueueStarting(this);
    }

    /**
     * @return the bare queue name, as opposed to the thread name.
     */
    @NonNull
    String getQueueName() {
        return mName;
    }

    /**
     * Terminate processing. (can't call this stop()...)
     */
    void terminate() {
        mStopping = true;
        interrupt();
    }

    /**
     * Main worker thread logic.
     */
    public void run() {
        final Context context = ServiceLocator.getLocalizedAppContext();
        try {
            while (!mStopping) {
                final ScheduledTask scheduledTask;
                final TQTask task;
                // All queue manipulation needs to be synchronized on the manager, as does
                // assignments of 'active' tasks in queues.
                synchronized (mQueueManager) {
                    scheduledTask = mQueueManager.getTaskQueueDao().getNextTask(mName);
                    if (scheduledTask == null) {
                        // No more tasks. Remove from manager and terminate.
                        mStopping = true;
                        mQueueManager.onQueueTerminating(this);
                        return;
                    }
                    if (scheduledTask.getMillisUntilRunnable() == 0) {
                        // Ready to run now.
                        task = scheduledTask.getTask();
                        mCurrentTaskReference = new WeakReference<>(task);
                    } else {
                        mCurrentTaskReference = null;
                        task = null;
                    }
                }

                // If we get here, we have a task, or know that there is one waiting to run.
                // Just wait for any wait that is longer than a minute.
                if (task != null) {
                    runTask(context, task);
                } else {
                    // Not ready, just wait. Allow for possible wake-up calls if something
                    // else gets queued.
                    synchronized (this) {
                        wait(scheduledTask.getMillisUntilRunnable());
                    }
                }
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
            Logger.error(TAG, e);
        } finally {
            try {
                // Just in case (the queue manager does check the queue before doing the delete).
                synchronized (mQueueManager) {
                    mQueueManager.onQueueTerminating(this);
                }
            } catch (@NonNull final RuntimeException ignore) {
                // ignore
            }
        }
    }

    /**
     * Run the task then save the results.
     */
    private void runTask(@NonNull final Context context,
                         @NonNull final TQTask task) {
        TQTask.TaskStatus status;
        try {
            task.setLastException(null);
            mQueueManager.notifyTaskChange();
            status = task.doWork(context);

        } catch (@NonNull final RuntimeException e) {
            // Don't overwrite exception set by handler
            if (task.getLastException() == null) {
                task.setLastException(e);
            }
            Logger.error(TAG, e, "Error running task " + task.getId());
            status = TQTask.TaskStatus.Failed;
        }

        // Update the related database record to process the task correctly.
        synchronized (mQueueManager) {
            final TaskQueueDao taskQueueDAO = mQueueManager.getTaskQueueDao();
            switch (status) {
                case Success:
                    taskQueueDAO.setTaskCompleted(task.getId());
                    break;

                case Requeue:
                    taskQueueDAO.requeueTask(task);
                    break;

                case Cancelled:
                    taskQueueDAO.deleteTask(task.getId());
                    break;

                case Failed:
                    final Exception e = task.getLastException();
                    final String msg;
                    if (e != null) {
                        msg = e.toString();
                    } else {
                        msg = "exception==null?";
                    }
                    taskQueueDAO.setTaskFailed(task, msg);
                    break;
            }

            //noinspection ConstantConditions
            mCurrentTaskReference.clear();
            mCurrentTaskReference = null;
        }
        mQueueManager.notifyTaskChange();
    }

    @Nullable
    TQTask getCurrentTask() {
        if (mCurrentTaskReference == null) {
            return null;
        } else {
            return mCurrentTaskReference.get();
        }
    }
}
