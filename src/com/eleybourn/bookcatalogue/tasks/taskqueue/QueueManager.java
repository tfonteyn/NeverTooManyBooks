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

package com.eleybourn.bookcatalogue.tasks.taskqueue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.EventActions;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.OnEventChangeListener;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.OnTaskChangeListener;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Listeners.TaskActions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Class to handle service-level aspects of the queues.
 *
 * Each defined queue results in a fresh Queue object being created in its own thread;
 * the QueueManager creates these. Queue objects last until there are no entries left in the queue.
 *
 * @author Philip Warner
 */
public class QueueManager {

    private static final String INTERNAL = "__internal";
    private static final String MESSAGE = "message";
    private static final String TOAST = "toast";

    /*
     * Queues we need:
     * main: long-running tasks, or tasks that can just wait
     * small_jobs: trivial background tasks that will only take a few seconds.
     */
    public static final String QUEUE_MAIN = "main";
    public static final String QUEUE_SMALL_JOBS = "small_jobs";

    /**
     * Static reference to the active QueueManager - singleton
     */
    private static QueueManager mInstance;

    /** Database access layer */
    @NonNull
    private final TaskQueueDBAdapter mDb;
    /** Collection of currently active queues */
    private final Map<String, Queue> mActiveQueues = new Hashtable<>();
    /** The UI thread */
    @NonNull
    private final WeakReference<Thread> mUIThread;
    /** Objects listening for Event operations */
    private final List<WeakReference<OnEventChangeListener>> mEventChangeListeners = new ArrayList<>();
    /** Objects listening for Task operations */
    @NonNull
    private final List<WeakReference<OnTaskChangeListener>> mTaskChangeListeners;

    /** Handle inter-thread messages */
    private MessageHandler mMessageHandler;

    /**
     * Reminder: as we're a singleton, we cannot cache the Context.
     * That would cause memory leaks according to Android docs.
     */
    private QueueManager() {
        if (mInstance != null) {
            /* This is an essential requirement because (a) synchronization will not work with
             more than one and (b) we want to store a static reference in the class. */
            throw new IllegalStateException("Only one QueueManager can be present");
        }
        mInstance = this;

        // Save the thread ... it is the UI thread
        mUIThread = new WeakReference<>(Thread.currentThread());
        // setup the handler with access to ourselves
        mMessageHandler = new MessageHandler(new WeakReference<>(this));

        mDb = new TaskQueueDBAdapter();

        // Get active queues.
        synchronized (this) {
            mDb.getAllQueues(this);
        }
        mTaskChangeListeners = new ArrayList<>();

        /*
         * Create the queues we need, if they do not already exist.
         */
        initializeQueue(QUEUE_MAIN);
        initializeQueue(QUEUE_SMALL_JOBS);
    }

    public static QueueManager getQueueManager() {
        if (mInstance == null) {
            mInstance = new QueueManager();
        }
        return mInstance;
    }

    void registerEventListener(final @NonNull OnEventChangeListener listener) {
        synchronized (mEventChangeListeners) {
            for (WeakReference<OnEventChangeListener> lr : mEventChangeListeners) {
                OnEventChangeListener l = lr.get();
                if (l != null && l.equals(listener)) {
                    return;
                }
            }
            mEventChangeListeners.add(new WeakReference<>(listener));
        }
    }

    void unregisterEventListener(final @NonNull OnEventChangeListener listener) {
        synchronized (mEventChangeListeners) {
            List<WeakReference<OnEventChangeListener>> ll = new ArrayList<>();
            for (WeakReference<OnEventChangeListener> l : mEventChangeListeners) {
                if (l.get().equals(listener)) {
                    ll.add(l);
                }
            }
            for (WeakReference<OnEventChangeListener> l : ll) {
                mEventChangeListeners.remove(l);
            }
        }
    }

    void registerTaskListener(final @NonNull OnTaskChangeListener listener) {
        synchronized (mTaskChangeListeners) {
            for (WeakReference<OnTaskChangeListener> lr : mTaskChangeListeners) {
                OnTaskChangeListener l = lr.get();
                if (l != null && l.equals(listener)) {
                    return;
                }
            }
            mTaskChangeListeners.add(new WeakReference<>(listener));
        }
    }

    void unregisterTaskListener(final @NonNull OnTaskChangeListener listener) {
        synchronized (mTaskChangeListeners) {
            List<WeakReference<OnTaskChangeListener>> ll = new ArrayList<>();
            for (WeakReference<OnTaskChangeListener> l : mTaskChangeListeners) {
                if (l.get().equals(listener)) {
                    ll.add(l);
                }
            }
            for (WeakReference<OnTaskChangeListener> l : ll) {
                mTaskChangeListeners.remove(l);
            }
        }
    }

    void notifyTaskChange(final @Nullable Task task, final @NonNull TaskActions action) {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<OnTaskChangeListener>> list;
        synchronized (mTaskChangeListeners) {
            list = new ArrayList<>(mTaskChangeListeners);
        }
        // Loop through the list. If the ref is dead, delete from original, otherwise call it.
        for (WeakReference<OnTaskChangeListener> wl : list) {
            final OnTaskChangeListener l = wl.get();
            if (l == null) {
                synchronized (mTaskChangeListeners) {
                    mTaskChangeListeners.remove(wl);
                }
            } else {
                try {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            l.onTaskChange(task, action);
                        }
                    };
                    mMessageHandler.post(r);
                } catch (Exception ignore) {
                }
            }
        }
    }

    private void notifyEventChange(final @Nullable Event event, final @NonNull EventActions action) {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<OnEventChangeListener>> list;
        synchronized (mEventChangeListeners) {
            list = new ArrayList<>(mEventChangeListeners);
        }
        // Loop through the list. If the ref is dead, delete from original, otherwise call it.
        for (WeakReference<OnEventChangeListener> wl : list) {
            final OnEventChangeListener l = wl.get();
            if (l == null) {
                synchronized (mEventChangeListeners) {
                    mEventChangeListeners.remove(wl);
                }
            } else {
                try {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            l.onEventChange(event, action);
                        }
                    };
                    mMessageHandler.post(r);
                } catch (Exception ignore) {
                }
            }
        }
    }

    /**
     * Store a Task in the database to run on the specified Queue and start queue if necessary.
     *
     * @param task      task to queue
     * @param queueName Name of queue
     */
    public void enqueueTask(final @NonNull Task task,
                            final @NonNull String queueName) {
        synchronized (this) {
            // Save it
            mDb.enqueueTask(task, queueName);
            if (mActiveQueues.containsKey(queueName)) {
                synchronized (mActiveQueues) {
                    mActiveQueues.get(queueName).notify();
                }
            } else {
                // Create the queue; it will start and add itself to the manager
                new Queue(this, queueName);
            }
        }
        this.notifyTaskChange(task, TaskActions.created);
    }

    /**
     * Create the specified queue if it does not exist.
     *
     * @param name Name of the queue
     */
    private void initializeQueue(final @NonNull String name) {
        mDb.createQueue(name);
    }

    /**
     * Called by a Queue object in its Constructor to inform the QueueManager of its existence
     *
     * @param queue New queue object
     */
    void onQueueStarting(final @NonNull Queue queue) {
        synchronized (this) {
            mActiveQueues.put(queue.getQueueName(), queue);
        }
    }

    /**
     * Called by the Queue object when it is terminating and no longer processing Tasks.
     *
     * @param queue Queue that is stopping
     */
    void onQueueTerminating(final @NonNull Queue queue) {
        synchronized (this) {
            try {
                // It's possible that a queue terminated and another started; make sure we are removing
                // the one that called us.
                Queue q = mActiveQueues.get(queue.getQueueName());
                if (q.equals(queue)) {
                    mActiveQueues.remove(queue.getQueueName());
                }
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Called by a Queue object to run a task. This method is in the QueueManager so that
     * it can be easily overridden by a subclass.
     *
     * @param task Task to run
     *
     * @return false to requeue, true for success
     */
    boolean runTask(final @NonNull Task task) {
        if (task instanceof GoodreadsTask) {
            return ((GoodreadsTask) task).run(this, BookCatalogueApp.getAppContext());
        } else {
            // Either extend RunnableTask, or override QueueManager.runTask()
            throw new IllegalStateException("Can not handle tasks that are not RunnableTasks");
        }
    }

    /**
     * Save the passed task back to the database. The parameter must be a Task that
     * is already in the database. This method is used to preserve a task state.
     *
     * @param task The task to be updated. Must exist in database.
     */
    public void updateTask(final @NonNull Task task) {
        mDb.updateTask(task);
        this.notifyTaskChange(task, TaskActions.updated);
    }

    /**
     * Make a toast message for the caller. Queue in UI thread if necessary.
     */
    private void doToast(final @Nullable String message) {
        if (Thread.currentThread() == mUIThread.get()) {
            synchronized (this) {
                StandardDialogs.showUserMessage(message);
            }
        } else {
            /* Send message to the handler */
            Message msg = mMessageHandler.obtainMessage();
            Bundle bundle = new Bundle();
            bundle.putString(INTERNAL, TOAST);
            bundle.putString(MESSAGE, message);
            msg.setData(bundle);
            mMessageHandler.sendMessage(msg);
        }
    }

    /**
     * Store an Event object for later retrieval after task has completed. This is
     * analogous to writing a line to the 'log file' for the task.
     *
     * @param t Related task
     * @param e Exception (usually subclassed)
     */
    void storeTaskEvent(final @NonNull Task t, final @NonNull Event e) {
        mDb.storeTaskEvent(t, e);
        this.notifyEventChange(e, EventActions.created);
    }

    /**
     * Return an EventsCursor for all events.
     *
     * @return Cursor of exceptions
     */
    @NonNull
    EventsCursor getAllEvents() {
        return mDb.getAllEvents();
    }

    /**
     * Return an EventsCursor for the specified task ID.
     *
     * @param taskId ID of the task
     *
     * @return Cursor of exceptions
     */
    @NonNull
    EventsCursor getTaskEvents(final long taskId) {
        return mDb.getTaskEvents(taskId);
    }

    /**
     * Return as TasksCursor for the specified type.
     *
     * @return Cursor of exceptions
     */
    @NonNull
    TasksCursor getTasks() {
        return mDb.getTasks();
    }

    /**
     * Return as TasksCursor for the specified category.
     *
     * @param category Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    public boolean hasActiveTasks(final long category) {
        try (TasksCursor c = mDb.getTasks(category)) {
            return c.moveToFirst();
        }
    }

    /**
     * Delete the specified Task object and related Event objects
     *
     * @param id ID of TaskException to delete.
     */
    void deleteTask(final long id) {
        boolean isActive = false;
        // Check if the task is running in a queue.
        synchronized (this) {
            // Synchronize so that no queue will be able to get another task while we are deleting
            for (Queue q : mActiveQueues.values()) {
                Task t = q.getTask();
                if (t != null && t.getId() == id) {
                    // Abort it, don't delete from DB...it will do that WHEN it aborts
                    t.abortTask();
                    isActive = true;
                }
            }
            if (!isActive) {
                mDb.deleteTask(id);
            }
        }
        if (isActive) {
            this.notifyEventChange(null, EventActions.updated);
            // This is non-optimal, but ... it's easy and clear.
            // Deleting an event MAY result in an orphan task being deleted.
            this.notifyTaskChange(null, TaskActions.updated);
        } else {
            this.notifyEventChange(null, EventActions.deleted);
            // This is non-optimal, but ... it's easy and clear.
            // Deleting an event MAY result in an orphan task being deleted.
            this.notifyTaskChange(null, TaskActions.deleted);
        }
    }

    /**
     * Delete the specified Event object.
     *
     * @param eventId ID of TaskException to delete.
     */
    public void deleteEvent(final long eventId) {
        mDb.deleteEvent(eventId);
        this.notifyEventChange(null, EventActions.deleted);
        // This is non-optimal, but ... it's easy and clear.
        // Deleting an event MAY result in an orphan task being deleted.
        this.notifyTaskChange(null, TaskActions.deleted);
    }

    /**
     * Delete Event records more than 7 days old.
     */
    void cleanupOldEvents() {
        mDb.cleanupOldEvents();
        mDb.cleanupOrphans();
        // This is non-optimal, but ... it's easy and clear.
        this.notifyEventChange(null, EventActions.deleted);
        this.notifyTaskChange(null, TaskActions.deleted);
    }

    /**
     * Delete Task records more than 7 days old.
     *
     */
    void cleanupOldTasks() {
        mDb.cleanupOldTasks();
        mDb.cleanupOrphans();
        // This is non-optimal, but ... it's easy and clear.
        this.notifyEventChange(null, EventActions.deleted);
        this.notifyTaskChange(null, TaskActions.deleted);
    }

    /**
     * Get a new Event object capable of representing a non-deserializable Event object.
     *
     * This method is used when deserialization fails, most likely as a result of changes
     * to the underlying serialized class.
     *
     * @param original	original serialization source
     */
    @NonNull
    LegacyEvent newLegacyEvent(byte[] original) {
        return new LegacyEvent(original);
    }

    /**
     * Get a new Task object capable of representing a non-deserializable Task object.
     *
     * @param original	original serialization source
     */
    @NonNull
    LegacyTask newLegacyTask(byte[] original) {
        return new LegacyTask(original);
    }

    /**
     * Handler for internal UI thread messages.
     */
    private static class MessageHandler extends Handler {

        @NonNull
        private final WeakReference<QueueManager> mQueueManager;

        MessageHandler(final @NonNull WeakReference<QueueManager> queueManager) {
            mQueueManager = queueManager;
        }

        public void handleMessage(final @NonNull Message msg) {
            Bundle bundle = msg.getData();
            if (bundle.containsKey(INTERNAL)) {
                String kind = bundle.getString(INTERNAL);
                if (TOAST.equals(kind)) {
                    mQueueManager.get().doToast(bundle.getString(MESSAGE));
                }
            } else {
                throw new IllegalArgumentException("Unknown message");
            }
        }
    }
}
