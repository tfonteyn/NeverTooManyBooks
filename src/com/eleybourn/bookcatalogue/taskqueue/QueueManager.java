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

    /** Static reference to the active QueueManager */
    private static QueueManager m_queueManager;
    /** Database access layer */
    private final DbAdapter m_dba;
    /** Collection of currently active queues */
    private final Map<String, Queue> m_activeQueues = new Hashtable<>();
    /** The UI thread */
    private final WeakReference<Thread> m_uiThread;
    /** Handle inter-thread messages */
    private final MessageHandler m_messageHandler;
    /** Objects listening for Event operations */
    private final List<WeakReference<OnEventChangeListener>> m_eventChangeListeners = new ArrayList<>();
    /** Objects listening for Task operations */
    private final List<WeakReference<OnTaskChangeListener>> m_taskChangeListeners;


    public QueueManager(@NonNull final Context context) {
        super();
        if (m_queueManager != null) {
            // This is an essential requirement because (a) synchronization will not work with more than one
            // and (b) we want to store a static reference in the class.
            throw new RuntimeException("Only one QueueManager can be present");
        }
        m_queueManager = this;

        // Save the thread ... it is the UI thread
        m_uiThread = new WeakReference<>(Thread.currentThread());
        m_messageHandler = new MessageHandler();

        m_dba = new DbAdapter(context.getApplicationContext());

        // Get active queues.
        synchronized (this) {
            m_dba.getAllQueues(this);
        }
        m_taskChangeListeners = new ArrayList<>();
    }

    public static QueueManager getQueueManager() {
        return m_queueManager;
    }

    public void registerEventListener(@NonNull final OnEventChangeListener listener) {
        synchronized (m_eventChangeListeners) {
            for (WeakReference<OnEventChangeListener> lr : m_eventChangeListeners) {
                OnEventChangeListener l = lr.get();
                if (l != null && l.equals(listener))
                    return;
            }
            m_eventChangeListeners.add(new WeakReference<>(listener));
        }
    }

    public void unregisterEventListener(@NonNull final OnEventChangeListener listener) {
        synchronized (m_eventChangeListeners) {
            List<WeakReference<OnEventChangeListener>> ll = new ArrayList<>();
            for (WeakReference<OnEventChangeListener> l : m_eventChangeListeners) {
                if (l.get().equals(listener))
                    ll.add(l);
            }
            for (WeakReference<OnEventChangeListener> l : ll) {
                m_eventChangeListeners.remove(l);
            }
        }
    }

    public void registerTaskListener(@NonNull final OnTaskChangeListener listener) {
        synchronized (m_taskChangeListeners) {
            for (WeakReference<OnTaskChangeListener> lr : m_taskChangeListeners) {
                OnTaskChangeListener l = lr.get();
                if (l != null && l.equals(listener))
                    return;
            }
            m_taskChangeListeners.add(new WeakReference<>(listener));
        }
    }

    public void unregisterTaskListener(@NonNull final OnTaskChangeListener listener) {
        synchronized (m_taskChangeListeners) {
            List<WeakReference<OnTaskChangeListener>> ll = new ArrayList<>();
            for (WeakReference<OnTaskChangeListener> l : m_taskChangeListeners) {
                if (l.get().equals(listener))
                    ll.add(l);
            }
            for (WeakReference<OnTaskChangeListener> l : ll) {
                m_taskChangeListeners.remove(l);
            }
        }
    }

    protected void notifyTaskChange(@Nullable final  Task task, @NonNull final  TaskActions action) {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<OnTaskChangeListener>> list;
        synchronized (m_taskChangeListeners) {
            list = new ArrayList<>(m_taskChangeListeners);
        }
        // Scan through the list. If the ref is dead, delete from original, otherwise call it.
        for (WeakReference<OnTaskChangeListener> wl : list) {
            final OnTaskChangeListener l = wl.get();
            if (l == null) {
                synchronized (m_taskChangeListeners) {
                    m_taskChangeListeners.remove(wl);
                }
            } else {
                try {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            l.onTaskChange(task, action);
                        }
                    };
                    m_messageHandler.post(r);
                } catch (Exception e) {
                    // Throw away errors.
                }
            }
        }
    }

    private void notifyEventChange(@Nullable final Event event, @NonNull final EventActions action) {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<OnEventChangeListener>> list;
        synchronized (m_eventChangeListeners) {
            list = new ArrayList<>(m_eventChangeListeners);
        }
        // Scan through the list. If the ref is dead, delete from original, otherwise call it.
        for (WeakReference<OnEventChangeListener> wl : list) {
            final OnEventChangeListener l = wl.get();
            if (l == null) {
                synchronized (m_eventChangeListeners) {
                    m_eventChangeListeners.remove(wl);
                }
            } else {
                try {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            l.onEventChange(event, action);
                        }
                    };
                    m_messageHandler.post(r);
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
            m_dba.enqueueTask(task, queueName);
            if (m_activeQueues.containsKey(queueName)) {
                Queue queue = m_activeQueues.get(queueName);
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
        m_dba.createQueue(name);
    }

    /**
     * Called by a Queue object in its Constructor to inform the QueueManager of its existence
     *
     * @param queue New queue object
     */
    public void queueStarting(@NonNull final Queue queue) {
        synchronized (this) {
            m_activeQueues.put(queue.getQueueName(), queue);
        }
    }

    /**
     * Called by the Queue object when it is terminating and no longer processing Tasks.
     *
     * @param queue Queue that is stopping
     */
    public void queueTerminating(@NonNull final Queue queue) {
        synchronized (this) {
            try {
                // It's possible that a queue terminated and another started; make sure we are removing
                // the one that called us.
                Queue q = m_activeQueues.get(queue.getQueueName());
                if (q.equals(queue))
                    m_activeQueues.remove(queue.getQueueName());
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
    protected boolean runOneTask(@NonNull final Task task) {
        if (task instanceof RunnableTask) {
            return ((RunnableTask) task).run(this, this.getApplicationContext());
        } else {
            throw new RuntimeException("Can not handle tasks that are not RunnableTasks. Either extend RunnableTask, or override QueueManager.runOneTask()");
        }
    }

    /**
     * Save the passed task back to the database. The parameter must be a Task that
     * is already in the database. This method is used to preserve a task state.
     *
     * @param task The task to be saved. Must exist in database.
     */
    public void saveTask(@NonNull final Task task) {
        m_dba.updateTask(task);
        this.notifyTaskChange(task, TaskActions.updated);
    }

    /**
     * Make a toast message for the caller. Queue in UI thread if necessary.
     */
    private void doToast(String message) {
        if (Thread.currentThread() == m_uiThread.get()) {
            synchronized (this) {
                android.widget.Toast.makeText(this.getApplicationContext(), message, android.widget.Toast.LENGTH_LONG).show();
            }
        } else {
            /* Send message to the handler */
            Message msg = m_messageHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("__internal", "toast");
            b.putString("message", message);
            msg.setData(b);
            m_messageHandler.sendMessage(msg);
        }
    }

    /**
     * Store an Event object for later retrieval after task has completed. This is
     * analogous to writing a line to the 'log file' for the task.
     *
     * @param t Related task
     * @param e Exception (usually subclassed)
     */
    public void storeTaskEvent(@NonNull final Task t, @NonNull final Event e) {
        m_dba.storeTaskEvent(t, e);
        this.notifyEventChange(e, EventActions.created);
    }

    /**
     * Return an EventsCursor for all events.
     *
     * @return Cursor of exceptions
     */
    public EventsCursor getAllEvents() {
        return m_dba.getAllEvents();
    }

    /**
     * Return an EventsCursor for the specified task ID.
     *
     * @param taskId ID of the task
     *
     * @return Cursor of exceptions
     */
    public EventsCursor getTaskEvents(final long taskId) {
        return m_dba.getTaskEvents(taskId);
    }

    /**
     * Return as TasksCursor for the specified type.
     *
     * @param type Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    public TasksCursor getTasks(@NonNull final TaskCursorSubtype type) {
        return m_dba.getTasks(type);
    }

    /**
     * Return as TasksCursor for the specified category.
     *
     * @param category Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    public boolean hasActiveTasks(final long category) {
        try (TasksCursor c = m_dba.getTasks(category, TaskCursorSubtype.active)) {
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
            for (Queue q : m_activeQueues.values()) {
                Task t = q.getTask();
                if (t != null && t.getId() == id) {
                    // Abort it, don't delete from DB...it will do that WHEN it aborts
                    t.abortTask();
                    isActive = true;
                }
            }
            if (!isActive)
                m_dba.deleteTask(id);
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
     * @param id ID of TaskException to delete.
     */
    public void deleteEvent(final long id) {
        m_dba.deleteEvent(id);
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
        m_dba.cleanupOldEvents(ageInDays);
        m_dba.cleanupOrphans();
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
        m_dba.cleanupOldTasks(ageInDays);
        m_dba.cleanupOrphans();
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
     *
     * @param original original serialization source

     */
    @NonNull
    public LegacyEvent newLegacyEvent(byte[] original) {
        return new LegacyEvent(original, "Legacy Event");
    }

    /**
     * Get a new Task object capable of representing a non-deserializable Task object.
     *
     * @param original original serialization source
     */
    @NonNull
    public LegacyTask newLegacyTask(byte[] original) {
        return new LegacyTask(original, "Legacy Task");
    }

    /**
     * Handler for internal UI thread messages.
     * FIXME TOMF
     */
    private class MessageHandler extends Handler {
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            if (b.containsKey("__internal")) {
                String kind = b.getString("__internal");
                if ("toast".equals(kind)) {
                    doToast(b.getString("message"));
                }
            } else {
                throw new RuntimeException("Unknown message");
            }
        }
    }

}
