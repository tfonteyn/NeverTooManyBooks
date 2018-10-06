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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.taskqueue.Listeners.EventActions;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.OnEventChangeListener;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.OnTaskChangeListener;
import com.eleybourn.bookcatalogue.taskqueue.Listeners.TaskActions;
import com.eleybourn.bookcatalogue.taskqueue.TasksCursor.TaskCursorSubtype;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * Class to handle service-level aspects of the queues.
 *
 * Each defined queue results in a fresh Queue object being created in its own thread; the QueueManager
 * creates these. Queue objects last until there are no entries left in the queue.
 *
 * ENHANCE: Split QueueManager into *Manager and *Service, and autocreate QueueManager which will start Service when queues need to execute. Service stops after last queue.
 * ENHANCE: Add a 'requiresNetwork()' task property
 * ENHANCE: Register a BroadcastReceiver for ConnectivityManager.CONNECTIVITY_ACTION. In the onReceive handler you can call NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO)
 * ENHANCE: Terminate queues if network not available and all jobs require network.
 * ENHANCE: Add a 'stopQueue(name, force)' method which kills a queue by terminating its thread (force=true), or by asking it to stop, waiting 30 seconds and killing it.
 *
 * @author Philip Warner
 */
public abstract class QueueManager {

    private static final String INTERNAL = "__internal";
    private static final String MESSAGE = "message";
    private static final String TOAST = "toast";

    /** Static reference to the active QueueManager */
    private static QueueManager mQueueManager;
    /** Database access layer */
    private final DBAdapter mDb;
    /** Collection of currently active queues */
    private final Map<String, Queue> mActiveQueues = new Hashtable<>();
    /** The UI thread */
    private final WeakReference<Thread> mUIThread;
    /** Handle inter-thread messages */
    private final MessageHandler mMessageHandler;
    /** Objects listening for Event operations */
    private final List<WeakReference<OnEventChangeListener>> mEventChangeListeners = new ArrayList<>();
    /** Objects listening for Task operations */
    private final List<WeakReference<OnTaskChangeListener>> mTaskChangeListeners;


    protected QueueManager(@NonNull final Context context) {
        super();
        if (mQueueManager != null) {
            // This is an essential requirement because (a) synchronization will not work with more than one
            // and (b) we want to store a static reference in the class.
            throw new IllegalStateException("Only one QueueManager can be present");
        }
        mQueueManager = this;

        // Save the thread ... it is the UI thread
        mUIThread = new WeakReference<>(Thread.currentThread());
        mMessageHandler = new MessageHandler();

        mDb = new DBAdapter(context.getApplicationContext());

        // Get active queues.
        synchronized (this) {
            mDb.getAllQueues(this);
        }
        mTaskChangeListeners = new ArrayList<>();
    }

    public static QueueManager getQueueManager() {
        return mQueueManager;
    }

    public void registerEventListener(@NonNull final OnEventChangeListener listener) {
        synchronized (mEventChangeListeners) {
            for (WeakReference<OnEventChangeListener> lr : mEventChangeListeners) {
                OnEventChangeListener l = lr.get();
                if (l != null && l.equals(listener))
                    return;
            }
            mEventChangeListeners.add(new WeakReference<>(listener));
        }
    }

    public void unregisterEventListener(@NonNull final OnEventChangeListener listener) {
        synchronized (mEventChangeListeners) {
            List<WeakReference<OnEventChangeListener>> ll = new ArrayList<>();
            for (WeakReference<OnEventChangeListener> l : mEventChangeListeners) {
                if (l.get().equals(listener))
                    ll.add(l);
            }
            for (WeakReference<OnEventChangeListener> l : ll) {
                mEventChangeListeners.remove(l);
            }
        }
    }

    public void registerTaskListener(@NonNull final OnTaskChangeListener listener) {
        synchronized (mTaskChangeListeners) {
            for (WeakReference<OnTaskChangeListener> lr : mTaskChangeListeners) {
                OnTaskChangeListener l = lr.get();
                if (l != null && l.equals(listener))
                    return;
            }
            mTaskChangeListeners.add(new WeakReference<>(listener));
        }
    }

    public void unregisterTaskListener(@NonNull final OnTaskChangeListener listener) {
        synchronized (mTaskChangeListeners) {
            List<WeakReference<OnTaskChangeListener>> ll = new ArrayList<>();
            for (WeakReference<OnTaskChangeListener> l : mTaskChangeListeners) {
                if (l.get().equals(listener))
                    ll.add(l);
            }
            for (WeakReference<OnTaskChangeListener> l : ll) {
                mTaskChangeListeners.remove(l);
            }
        }
    }

    void notifyTaskChange(@Nullable final Task task, @NonNull final TaskActions action) {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<OnTaskChangeListener>> list;
        synchronized (mTaskChangeListeners) {
            list = new ArrayList<>(mTaskChangeListeners);
        }
        // Scan through the list. If the ref is dead, delete from original, otherwise call it.
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
                } catch (Exception e) {
                    // Throw away errors.
                }
            }
        }
    }

    private void notifyEventChange(@Nullable final Event event, @NonNull final EventActions action) {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<OnEventChangeListener>> list;
        synchronized (mEventChangeListeners) {
            list = new ArrayList<>(mEventChangeListeners);
        }
        // Scan through the list. If the ref is dead, delete from original, otherwise call it.
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
    public void enqueueTask(@NonNull final Task task, @NonNull final String queueName) {
        synchronized (this) {
            // Save it
            mDb.enqueueTask(task, queueName);
            if (mActiveQueues.containsKey(queueName)) {
                Queue queue = mActiveQueues.get(queueName);
                synchronized (queue) {
                    queue.notify();
                }
            } else {
                // Create the queue; it will start and add itself to the manager
                new Queue(this.getApplicationContext(), this, queueName);
            }
        }
        this.notifyTaskChange(task, TaskActions.created);
    }

    /**
     * Create the specified queue if it does not exist.
     *
     * @param name Name of the queue
     */
    protected void initializeQueue(@NonNull final String name) {
        mDb.createQueue(name);
    }

    /**
     * Called by a Queue object in its Constructor to inform the QueueManager of its existence
     *
     * @param queue New queue object
     */
    void queueStarting(@NonNull final Queue queue) {
        synchronized (this) {
            mActiveQueues.put(queue.getQueueName(), queue);
        }
    }

    /**
     * Called by the Queue object when it is terminating and no longer processing Tasks.
     *
     * @param queue Queue that is stopping
     */
    void queueTerminating(@NonNull final Queue queue) {
        synchronized (this) {
            try {
                // It's possible that a queue terminated and another started; make sure we are removing
                // the one that called us.
                Queue q = mActiveQueues.get(queue.getQueueName());
                if (q.equals(queue))
                    mActiveQueues.remove(queue.getQueueName());
            } catch (Exception e) {
                // Ignore failures.
            }
        }
    }

    /**
     * Called by a Queue object to run a task. This method is in the QueueManager so that
     * it can be easily overridden by a subclass.
     *
     * @param task Task to run
     *
     * @return Result from run(...) method
     */
    boolean runOneTask(@NonNull final Task task) {
        if (task instanceof RunnableTask) {
            return ((RunnableTask) task).run(this, this.getApplicationContext());
        } else {
            // Either extend RunnableTask, or override QueueManager.runOneTask()
            throw new IllegalStateException("Can not handle tasks that are not RunnableTasks");
        }
    }

    /**
     * Save the passed task back to the database. The parameter must be a Task that
     * is already in the database. This method is used to preserve a task state.
     *
     * @param task The task to be saved. Must exist in database.
     */
    public void saveTask(@NonNull final Task task) {
        mDb.updateTask(task);
        this.notifyTaskChange(task, TaskActions.updated);
    }

    /**
     * Make a toast message for the caller. Queue in UI thread if necessary.
     */
    private void doToast(@NonNull final String message) {
        if (Thread.currentThread() == mUIThread.get()) {
            synchronized (this) {
                Toast.makeText(this.getApplicationContext(), message, android.widget.Toast.LENGTH_LONG).show();
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
    void storeTaskEvent(@NonNull final Task t, @NonNull final Event e) {
        mDb.storeTaskEvent(t, e);
        this.notifyEventChange(e, EventActions.created);
    }

    /**
     * Return an EventsCursor for all events.
     *
     * @return Cursor of exceptions
     */
    public EventsCursor getAllEvents() {
        return mDb.getAllEvents();
    }

    /**
     * Return an EventsCursor for the specified task ID.
     *
     * @param taskId ID of the task
     *
     * @return Cursor of exceptions
     */
    public EventsCursor getTaskEvents(final long taskId) {
        return mDb.getTaskEvents(taskId);
    }

    /**
     * Return as TasksCursor for the specified type.
     *
     * @param type Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    public TasksCursor getTasks(@NonNull final TaskCursorSubtype type) {
        return mDb.getTasks(type);
    }

    /**
     * Return as TasksCursor for the specified category.
     *
     * @param category Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    public boolean hasActiveTasks(final long category) {
        try (TasksCursor c = mDb.getTasks(category, TaskCursorSubtype.active)) {
            return c.moveToFirst();
        }
    }

    /**
     * Delete the specified Task object and related Event objects
     *
     * @param id ID of TaskException to delete.
     */
    public void deleteTask(final long id) {
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
            if (!isActive)
                mDb.deleteTask(id);
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
     * Delete Event records more than a certain age.
     *
     * @param ageInDays Age in days for stale records
     */
    public void cleanupOldEvents(final int ageInDays) {
        mDb.cleanupOldEvents(ageInDays);
        mDb.cleanupOrphans();
        // This is non-optimal, but ... it's easy and clear.
        this.notifyEventChange(null, EventActions.deleted);
        this.notifyTaskChange(null, TaskActions.deleted);
    }

    /**
     * Delete Task records more than a certain age.
     *
     * @param ageInDays Age in days for stale records
     */
    public void cleanupOldTasks(final int ageInDays) {
        mDb.cleanupOldTasks(ageInDays);
        mDb.cleanupOrphans();
        // This is non-optimal, but ... it's easy and clear.
        this.notifyEventChange(null, EventActions.deleted);
        this.notifyTaskChange(null, TaskActions.deleted);
    }

    /**
     * Return the running application context.
     */
    protected abstract Context getApplicationContext();

    /**
     * Get a new Event object capable of representing a non-deserializable Event object.
     */
    @NonNull
    LegacyEvent newLegacyEvent() {
        return new LegacyEvent();
    }

    /**
     * Get a new Task object capable of representing a non-deserializable Task object.
     */
    @NonNull
    public LegacyTask newLegacyTask() {
        return new LegacyTask();
    }

    /**
     * Handler for internal UI thread messages.
     * FIXME TOMF must be static or leaks. See doToast... UI thread or not. investigate
     */
    private class MessageHandler extends Handler {
        public void handleMessage(@NonNull final Message msg) {
            Bundle bundle = msg.getData();
            if (bundle.containsKey(INTERNAL)) {
                String kind = bundle.getString(INTERNAL);
                if (TOAST.equals(kind)) {
                    doToast(bundle.getString(MESSAGE));
                }
            } else {
                throw new IllegalArgumentException("Unknown message");
            }
        }
    }

}
