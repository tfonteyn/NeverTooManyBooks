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
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Class to handle service-level aspects of the queues.
 * <p>
 * Each defined queue results in a fresh Queue object being created in its own thread;
 * the QueueManager creates these. Queue objects last until there are no entries left in the queue.
 */
public final class QueueManager {

    /** Queue: long-running tasks, or tasks that can just wait. */
    public static final String Q_MAIN = "main";

    /** Queue: trivial background tasks that will only take a few seconds. */
    public static final String Q_SMALL_JOBS = "small_jobs";
    /** Static reference to the active QueueManager - singleton. */
    private static QueueManager sInstance;
    /** Database access layer. */
    @NonNull
    private final QueueDAO mQueueDAO;
    /** Collection of currently active queues. */
    private final Map<String, Queue> mActiveQueues = new HashMap<>();
    /** Objects listening for Event operations. */
    @NonNull
    private final List<WeakReference<OnChangeListener>> mEventChangeListeners = new ArrayList<>();
    /** Objects listening for Task operations. */
    @NonNull
    private final List<WeakReference<OnChangeListener>> mTaskChangeListeners = new ArrayList<>();
    /** Handle inter-thread messages. */
    @Nullable
    private final Handler mHandler;

    /**
     * Constructor.
     *
     * @param context Current context; NOT stored.
     */
    private QueueManager(@NonNull final Context context) {
        if (sInstance != null) {
            // This is an essential requirement because (a) synchronization will not work with
            // more than one and (b) we want to store a static reference in the class.
            throw new IllegalStateException("Only one QueueManager can be present");
        }
        sInstance = this;

        // This is for testing when we don't have a looper
        final Looper looper = Looper.getMainLooper();
        if (looper != null) {
            mHandler = new Handler(looper);
        } else {
            mHandler = null;
        }

        mQueueDAO = new QueueDAO(context);
        // Create the queues we need, if they do not already exist.
        mQueueDAO.createQueue(Q_MAIN);
        mQueueDAO.createQueue(Q_SMALL_JOBS);
    }

    /**
     * Constructor. Called during startup so processing can start immediately.
     *
     * @param context Current context; NOT stored.
     */
    public static void start(@NonNull final Context context) {
        if (sInstance == null) {
            // This (re)starts stored tasks.
            // Note this is not a startup-task; it just needs to be started at startup.
            // (it does not even need to be a background thread, as we only want
            // to create the QM, but this way we should get the UI up faster)
            final Thread qmt = new Thread(() -> {
                sInstance = new QueueManager(context);
                sInstance.start();
            });
            qmt.setName("QueueManager-create");
            qmt.start();
        }
    }

    /**
     * Get the singleton.
     *
     * @return instance
     */
    @NonNull
    public static QueueManager getInstance() {
        // do not lazy initialize here. We want the QueueManager running at startup.
        return Objects.requireNonNull(sInstance, "create was not called?");
    }

    /**
     * Start the active queues if not already started.
     */
    public void start() {
        synchronized (this) {
            if (mActiveQueues.isEmpty()) {
                mQueueDAO.initAllQueues(this);
            }
        }
    }

    public void registerTaskListener(@NonNull final OnChangeListener listener) {
        synchronized (mTaskChangeListeners) {
            for (final WeakReference<OnChangeListener> lr : mTaskChangeListeners) {
                final OnChangeListener l = lr.get();
                if (listener.equals(l)) {
                    return;
                }
            }
            mTaskChangeListeners.add(new WeakReference<>(listener));
        }
    }

    public void unregisterTaskListener(@NonNull final OnChangeListener listener) {
        try {
            synchronized (mTaskChangeListeners) {
                final Collection<WeakReference<OnChangeListener>> ll = new ArrayList<>();
                for (final WeakReference<OnChangeListener> l : mTaskChangeListeners) {
                    if (l.get().equals(listener)) {
                        ll.add(l);
                    }
                }
                for (final WeakReference<OnChangeListener> l : ll) {
                    mTaskChangeListeners.remove(l);
                }
            }
        } catch (@NonNull final RuntimeException ignore) {
            // ignore
        }
    }

    public void registerEventListener(@NonNull final OnChangeListener listener) {
        synchronized (mEventChangeListeners) {
            for (final WeakReference<OnChangeListener> lr : mEventChangeListeners) {
                final OnChangeListener l = lr.get();
                if (listener.equals(l)) {
                    return;
                }
            }
            mEventChangeListeners.add(new WeakReference<>(listener));
        }
    }

    public void unregisterEventListener(@NonNull final OnChangeListener listener) {
        try {
            synchronized (mEventChangeListeners) {
                final Collection<WeakReference<OnChangeListener>> ll = new ArrayList<>();
                for (final WeakReference<OnChangeListener> l : mEventChangeListeners) {
                    if (l.get().equals(listener)) {
                        ll.add(l);
                    }
                }
                for (final WeakReference<OnChangeListener> l : ll) {
                    mEventChangeListeners.remove(l);
                }
            }
        } catch (@NonNull final RuntimeException ignore) {
            // ignore
        }
    }

    void notifyTaskChange() {
        // Make a copy of the list so we can cull dead elements from the original
        final List<WeakReference<OnChangeListener>> list;
        synchronized (mTaskChangeListeners) {
            list = new ArrayList<>(mTaskChangeListeners);
        }
        // Loop through the list. If the ref is dead, delete from original, otherwise call it.
        for (final WeakReference<OnChangeListener> wl : list) {
            final OnChangeListener listener = wl.get();
            if (listener == null) {
                synchronized (mTaskChangeListeners) {
                    mTaskChangeListeners.remove(wl);
                }
            } else {
                if (mHandler != null) {
                    try {
                        mHandler.post(listener::onChange);
                    } catch (@NonNull final RuntimeException ignore) {
                        // ignore
                    }
                }
            }
        }
    }

    private void notifyEventChange() {
        // Make a copy of the list so we can cull dead elements from the original
        final List<WeakReference<OnChangeListener>> list;
        synchronized (mEventChangeListeners) {
            list = new ArrayList<>(mEventChangeListeners);
        }
        // Loop through the list. If the ref is dead, delete from original, otherwise call it.
        for (final WeakReference<OnChangeListener> wl : list) {
            final OnChangeListener listener = wl.get();
            if (listener == null) {
                synchronized (mEventChangeListeners) {
                    mEventChangeListeners.remove(wl);
                }
            } else {
                if (mHandler != null) {
                    try {
                        mHandler.post(listener::onChange);
                    } catch (@NonNull final RuntimeException ignore) {
                        // ignore
                    }
                }
            }
        }
    }

    /**
     * Store a Task in the database to run on the specified Queue and start queue if necessary.
     *
     * @param queueName Name of queue
     * @param task      task to queue
     */
    public void enqueueTask(@NonNull final String queueName,
                            @NonNull final TQTask task) {
        synchronized (this) {
            // Save it first
            mQueueDAO.enqueueTask(task, queueName);

            // notify the queue there is work to do
            final Queue queue = mActiveQueues.get(queueName);
            if (queue != null) {
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (queue) {
                    // Reminder: notify() **MUST** be called inside
                    // a synchronized block on the object itself
                    queue.notify();
                }
            } else {
                new Queue(this, queueName).start();
            }
        }
        notifyTaskChange();
    }

    /**
     * Called by a Queue object in its Constructor to inform the QueueManager of its existence.
     *
     * @param queue New queue object
     */
    void onQueueStarting(@NonNull final Queue queue) {
        synchronized (this) {
            mActiveQueues.put(queue.getQueueName(), queue);
        }
    }

    /**
     * Called by the Queue object when it is terminating and no longer processing Tasks.
     *
     * @param queue Queue that is stopping
     */
    void onQueueTerminating(@NonNull final Queue queue) {
        synchronized (this) {
            try {
                // It's possible that a queue terminated and another started; make sure
                // we are removing the one that called us.
                if (queue.equals(mActiveQueues.get(queue.getQueueName()))) {
                    mActiveQueues.remove(queue.getQueueName());
                }
            } catch (@NonNull final RuntimeException ignore) {
                // ignore
            }
        }
    }

    /**
     * Save the passed task back to the database. The parameter must be a Task that
     * is already in the database. This method is used to preserve a task state.
     *
     * @param task The task to be updated. Must exist in database.
     */
    public void updateTask(@NonNull final TQTask task) {
        mQueueDAO.updateTask(task);
        notifyTaskChange();
    }

    /**
     * Store an Event object for later retrieval after task has completed. This is
     * analogous to writing a line to the 'log file' for the task.
     *
     * @param taskId Related task
     * @param event  Event
     */
    void storeTaskEvent(final long taskId,
                        @NonNull final TQEvent event) {
        mQueueDAO.storeTaskEvent(taskId, event);
        notifyEventChange();
    }

    /**
     * Get a Cursor returning all events.
     *
     * @return Cursor
     */
    @NonNull
    public Cursor getEvents() {
        return mQueueDAO.getEvents();
    }

    /**
     * Get a Cursor returning all events for the passed task.
     *
     * @param taskId id of the task whose exceptions we want
     *
     * @return Cursor
     */
    @NonNull
    public Cursor getEvents(final long taskId) {
        return mQueueDAO.getEvents(taskId);
    }

    /**
     * Get a Cursor returning all tasks.
     *
     * @return Cursor
     */
    @NonNull
    public Cursor getTasks() {
        return mQueueDAO.getTasks();
    }

    /**
     * Check if there are active tasks of the specified category.
     *
     * @param category Category to get
     *
     * @return {@code true} if there are active tasks
     */
    public boolean hasActiveTasks(final long category) {
        return mQueueDAO.hasActiveTasks(category);
    }

    /**
     * Delete the specified Task object and related Event objects.
     *
     * @param id of Task to delete.
     */
    void deleteTask(final long id) {
        // Synchronize so that no queue will be able to get another task while we are deleting
        synchronized (this) {
            boolean isActive = false;
            // Check if the task is running in a queue.
            for (final Queue queue : mActiveQueues.values()) {
                final TQTask task = queue.getTask();
                if (task != null && task.getId() == id) {
                    // Abort it, don't delete from DB...it will do that WHEN it aborts
                    task.cancel();
                    isActive = true;
                }
            }
            if (!isActive) {
                mQueueDAO.deleteTask(id);
            }
        }

        // This is non-optimal, but ... it's easy and clear.
        notifyEventChange();
        // Deleting an event MAY result in an orphan task being deleted.
        notifyTaskChange();
    }

    /**
     * Delete the specified Event object.
     *
     * @param id of Event to delete.
     */
    public void deleteEvent(final long id) {
        mQueueDAO.deleteEvent(id);
        notifyEventChange();
        notifyTaskChange();
    }

    /**
     * Delete Tasks (and its Events) more than 'days' old.
     *
     * @param days age
     */
    public void deleteTasksOlderThan(final int days) {
        mQueueDAO.deleteTasksOlderThan(days);
        notifyEventChange();
        notifyTaskChange();
    }

    /**
     * Delete Events more 'days' old.
     *
     * @param days age
     */
    public void deleteEventsOlderThan(final int days) {
        mQueueDAO.deleteEventsOlderThan(days);
        notifyEventChange();
        notifyTaskChange();
    }

    public interface OnChangeListener {

        void onChange();
    }
}
