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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.BaseTQTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDAO.ScheduledTask;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;

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
    private WeakReference<TQTask> mTask;

    /** Flag to indicate process is terminating. */
    private boolean mTerminate;

    /**
     * Constructor. Nothing to see here, move along. Just save the properties and start the thread.
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
        final Context context = AppLocale.getInstance().apply(App.getTaskContext());
        try (final QueueDAO queueDAO = new QueueDAO(context)) {
            while (!mTerminate) {
                final ScheduledTask scheduledTask;
                final TQTask task;
                // All queue manipulation needs to be synchronized on the manager, as does
                // assignments of 'active' tasks in queues.
                synchronized (mQueueManager) {
                    scheduledTask = queueDAO.getNextTask(context, mName);
                    if (scheduledTask == null) {
                        // No more tasks. Remove from manager and terminate.
                        mTerminate = true;
                        mQueueManager.onQueueTerminating(this);
                        return;
                    }
                    if (scheduledTask.getMillisUntilRunnable() == 0) {
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
                    runTask(context, queueDAO, task);
                } else {
                    // Not ready, just wait. Allow for possible wake-up calls if something
                    // else gets queued.
                    synchronized (this) {
                        wait(scheduledTask.getMillisUntilRunnable());
                    }
                }
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") @NonNull final Exception e) {
            Logger.error(context, TAG, e);
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
                         @NonNull final QueueDAO queueDAO,
                         @NonNull final TQTask task) {
        boolean success = false;
        boolean requeue = false;
        try {
            task.setLastException(null);
            mQueueManager.notifyTaskChange();

            if (task instanceof BaseTQTask) {
                success = ((BaseTQTask) task).run(context, mQueueManager);
            } else {
                // Either extend Task, or override QueueManager.runTask()
                throw new IllegalStateException("Can not handle tasks that are not BaseTQTask");
            }
            requeue = !success;

        } catch (@NonNull final RuntimeException e) {
            // Don't overwrite exception set by handler
            if (task.getLastException() == null) {
                task.setLastException(e);
            }
            Logger.error(context, TAG, e, "Error running task " + task.getId());
        }

        // Update the related database record to process the task correctly.
        synchronized (mQueueManager) {
            if (task.isCancelled()) {
                queueDAO.deleteTask(task.getId());
            } else if (success) {
                queueDAO.setTaskCompleted(task.getId());
            } else if (requeue) {
                queueDAO.requeueTask(task);
            } else {
                final Exception e = task.getLastException();
                String msg = null;
                if (e != null) {
                    msg = e.getLocalizedMessage();
                }
                queueDAO.setTaskFailed(task, "Unhandled exception while running task: " + msg);
            }
            mTask.clear();
            mTask = null;
        }
        mQueueManager.notifyTaskChange();
    }

    @Nullable
    public TQTask getTask() {
        if (mTask == null) {
            return null;
        } else {
            return mTask.get();
        }
    }
}
