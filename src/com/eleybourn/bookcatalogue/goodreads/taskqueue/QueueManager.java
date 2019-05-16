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

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eleybourn.bookcatalogue.App;

/**
 * Class to handle service-level aspects of the queues.
 * <p>
 * Each defined queue results in a fresh Queue object being created in its own thread;
 * the QueueManager creates these. Queue objects last until there are no entries left in the queue.
 *
 * @author Philip Warner
 */
public final class QueueManager {

    /*
     * Queues we need.
     */
    /**  main: long-running tasks, or tasks that can just wait. */
    public static final String Q_MAIN = "main";
    /** small_jobs: trivial background tasks that will only take a few seconds. */
    public static final String Q_SMALL_JOBS = "small_jobs";

    /** For internal message sending. */
    private static final String INTERNAL = "__internal";
    /** Type of INTERNAL message. */
    private static final String TOAST = "toast";
    /** Key for actual (text) message. */
    private static final String MESSAGE = "message";
    /**
     * Static reference to the active QueueManager - singleton.
     */
    private static QueueManager sInstance;

    /** Database access layer. */
    @NonNull
    private final TaskQueueDAO mTaskQueueDAO;
    /** Collection of currently active queues. */
    private final Map<String, Queue> mActiveQueues = new HashMap<>();
    /** The UI thread. */
    @NonNull
    private final WeakReference<Thread> mUIThread;
    /** Objects listening for Event operations. */
    private final List<WeakReference<ChangeListener>> mEventChangeListeners =
            new ArrayList<>();
    /** Objects listening for Task operations. */
    @NonNull
    private final List<WeakReference<ChangeListener>> mTaskChangeListeners;

    /** Handle inter-thread messages. */
    private final MessageHandler mMessageHandler;

    /**
     * Reminder: as we're a singleton, we cannot cache the Context.
     * That would cause memory leaks according to Android docs.
     */
    private QueueManager() {
        if (sInstance != null) {
            /* This is an essential requirement because (a) synchronization will not work with
             more than one and (b) we want to store a static reference in the class. */
            throw new IllegalStateException("Only one QueueManager can be present");
        }
        sInstance = this;

        // Save the thread ... it is the UI thread
        mUIThread = new WeakReference<>(Thread.currentThread());
        // setup the handler with access to ourselves
        mMessageHandler = new MessageHandler(new WeakReference<>(this));

        mTaskQueueDAO = new TaskQueueDAO();

        // Get active queues.
        synchronized (this) {
            mTaskQueueDAO.getAllQueues(this);
        }
        mTaskChangeListeners = new ArrayList<>();

        /*
         * Create the queues we need, if they do not already exist.
         */
        initializeQueue(Q_MAIN);
        initializeQueue(Q_SMALL_JOBS);
    }

    public static void init() {
        if (sInstance == null) {
            sInstance = new QueueManager();
        }
    }

    public static QueueManager getQueueManager() {
        if (sInstance == null) {
            throw new IllegalStateException("init was not called?");
        }
        return sInstance;
    }

    void registerEventListener(@NonNull final ChangeListener listener) {
        synchronized (mEventChangeListeners) {
            for (WeakReference<ChangeListener> lr : mEventChangeListeners) {
                ChangeListener l = lr.get();
                if (l != null && l.equals(listener)) {
                    return;
                }
            }
            mEventChangeListeners.add(new WeakReference<>(listener));
        }
    }

    /** ignores any failures. */
    void unregisterEventListener(@NonNull final ChangeListener listener) {
        try {
            synchronized (mEventChangeListeners) {
                List<WeakReference<ChangeListener>> ll = new ArrayList<>();
                for (WeakReference<ChangeListener> l : mEventChangeListeners) {
                    if (l.get().equals(listener)) {
                        ll.add(l);
                    }
                }
                for (WeakReference<ChangeListener> l : ll) {
                    mEventChangeListeners.remove(l);
                }
            }
        } catch (RuntimeException ignore) {
        }
    }

    void registerTaskListener(@NonNull final ChangeListener listener) {
        synchronized (mTaskChangeListeners) {
            for (WeakReference<ChangeListener> lr : mTaskChangeListeners) {
                ChangeListener l = lr.get();
                if (l != null && l.equals(listener)) {
                    return;
                }
            }
            mTaskChangeListeners.add(new WeakReference<>(listener));
        }
    }

    /** ignores any failures. */
    void unregisterTaskListener(@NonNull final ChangeListener listener) {
        try {
            synchronized (mTaskChangeListeners) {
                List<WeakReference<ChangeListener>> ll = new ArrayList<>();
                for (WeakReference<ChangeListener> l : mTaskChangeListeners) {
                    if (l.get().equals(listener)) {
                        ll.add(l);
                    }
                }
                for (WeakReference<ChangeListener> l : ll) {
                    mTaskChangeListeners.remove(l);
                }
            }
        } catch (RuntimeException ignore) {
        }
    }

    void notifyTaskChange() {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<ChangeListener>> list;
        synchronized (mTaskChangeListeners) {
            list = new ArrayList<>(mTaskChangeListeners);
        }
        // Loop through the list. If the ref is dead, delete from original, otherwise call it.
        for (WeakReference<ChangeListener> wl : list) {
            final ChangeListener listener = wl.get();
            if (listener == null) {
                synchronized (mTaskChangeListeners) {
                    mTaskChangeListeners.remove(wl);
                }
            } else {
                try {
                    mMessageHandler.post(listener::onChange);
                } catch (RuntimeException ignore) {
                }
            }
        }
    }

    private void notifyEventChange() {
        // Make a copy of the list so we can cull dead elements from the original
        List<WeakReference<ChangeListener>> list;
        synchronized (mEventChangeListeners) {
            list = new ArrayList<>(mEventChangeListeners);
        }
        // Loop through the list. If the ref is dead, delete from original, otherwise call it.
        for (WeakReference<ChangeListener> wl : list) {
            final ChangeListener listener = wl.get();
            if (listener == null) {
                synchronized (mEventChangeListeners) {
                    mEventChangeListeners.remove(wl);
                }
            } else {
                try {
                    mMessageHandler.post(listener::onChange);
                } catch (RuntimeException ignore) {
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
    public void enqueueTask(@NonNull final Task task,
                            @NonNull final String queueName) {
        synchronized (this) {
            // Save it
            mTaskQueueDAO.enqueueTask(task, queueName);

            if (mActiveQueues.containsKey(queueName)) {
                // notify the queue there is work to do
                synchronized (mActiveQueues) {
                    //noinspection ConstantConditions
                    mActiveQueues.get(queueName).notify();
                }
            } else {
                // Create the queue; it will start and add itself to the manager
                new Queue(this, queueName);
            }
        }
        notifyTaskChange();
    }

    /**
     * Create the specified queue if it does not exist.
     *
     * @param name Name of the queue
     */
    private void initializeQueue(@NonNull final String name) {
        mTaskQueueDAO.createQueue(name);
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
            } catch (RuntimeException ignore) {
            }
        }
    }

    /**
     * Called by a Queue object to run a task. This method is in the QueueManager so that
     * it can be easily overridden by a subclass.
     *
     * @param task Task to run
     *
     * @return {@code false} to requeue, {@code true} for success
     */
    boolean runTask(@NonNull final Task task) {
        if (task instanceof BaseTask) {
            return ((BaseTask) task).run(this, App.getAppContext());
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
    public void updateTask(@NonNull final Task task) {
        mTaskQueueDAO.updateTask(task);
        notifyTaskChange();
    }

    /**
     * Make a toast message for the caller. Queue in UI thread if necessary.
     * <p>
     * Hardwired to use {@link Toast} as there is no way to get an activity or view
     * for using SnackBar.
     */
    private void doToast(@NonNull final String message) {
        if (Thread.currentThread() == mUIThread.get()) {
            synchronized (this) {
                Toast.makeText(App.getAppContext(), message, Toast.LENGTH_LONG).show();
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
     * @param task  Related task
     * @param event Exception (usually subclassed)
     */
    void storeTaskEvent(@NonNull final Task task,
                        @NonNull final Event event) {
        mTaskQueueDAO.storeTaskEvent(task, event);
        notifyEventChange();
    }

    /**
     * Return an EventsCursor for all events.
     *
     * @return Cursor of exceptions
     */
    @NonNull
    EventsCursor getAllEvents() {
        return mTaskQueueDAO.getAllEvents();
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
        return mTaskQueueDAO.getTaskEvents(taskId);
    }

    /**
     * Return as TasksCursor for the specified type.
     *
     * @return Cursor of exceptions
     */
    @NonNull
    TasksCursor getTasks() {
        return mTaskQueueDAO.getTasks();
    }

    /**
     * Return as TasksCursor for the specified category.
     *
     * @param category Subtype of cursor to retrieve
     *
     * @return Cursor of exceptions
     */
    public boolean hasActiveTasks(final long category) {
        try (TasksCursor c = mTaskQueueDAO.getTasks(category)) {
            return c.moveToFirst();
        }
    }

    /**
     * Delete the specified Task object and related Event objects.
     *
     * @param id ID of TaskException to delete.
     */
    void deleteTask(final long id) {
        boolean isActive = false;
        // Check if the task is running in a queue.
        synchronized (this) {
            // Synchronize so that no queue will be able to get another task while we are deleting
            for (Queue queue : mActiveQueues.values()) {
                Task task = queue.getTask();
                if (task != null && task.getId() == id) {
                    // Abort it, don't delete from DB...it will do that WHEN it aborts
                    task.abortTask();
                    isActive = true;
                }
            }
            if (!isActive) {
                mTaskQueueDAO.deleteTask(id);
            }
        }
        if (isActive) {
            notifyEventChange();
            // This is non-optimal, but ... it's easy and clear.
            // Deleting an event MAY result in an orphan task being deleted.
            notifyTaskChange();
        } else {
            notifyEventChange();
            // This is non-optimal, but ... it's easy and clear.
            // Deleting an event MAY result in an orphan task being deleted.
            notifyTaskChange();
        }
    }

    /**
     * Delete the specified Event object.
     *
     * @param eventId ID of TaskException to delete.
     */
    public void deleteEvent(final long eventId) {
        mTaskQueueDAO.deleteEvent(eventId);
        notifyEventChange();
        // This is non-optimal, but ... it's easy and clear.
        // Deleting an event MAY result in an orphan task being deleted.
        notifyTaskChange();
    }

    /**
     * Delete Event records more than 7 days old.
     */
    void cleanupOldEvents() {
        mTaskQueueDAO.cleanupOldEvents();
        mTaskQueueDAO.cleanupOrphans();
        // This is non-optimal, but ... it's easy and clear.
        notifyEventChange();
        notifyTaskChange();
    }

    /**
     * Delete Task records more than 7 days old.
     */
    void cleanupOldTasks() {
        mTaskQueueDAO.cleanupOldTasks();
        mTaskQueueDAO.cleanupOrphans();
        // This is non-optimal, but ... it's easy and clear.
        notifyEventChange();
        notifyTaskChange();
    }

    public interface ChangeListener {

        void onChange();
    }

    /**
     * Handler for internal UI thread messages.
     */
    private static class MessageHandler
            extends Handler {

        @NonNull
        private final WeakReference<QueueManager> mQueueManager;

        /**
         * Constructor.
         *
         * @param queueManager the manager
         */
        MessageHandler(@NonNull final WeakReference<QueueManager> queueManager) {
            mQueueManager = queueManager;
        }

        public void handleMessage(@NonNull final Message msg) {
            Bundle bundle = msg.getData();
            if (bundle.containsKey(INTERNAL)) {
                String kind = bundle.getString(INTERNAL);
                if (TOAST.equals(kind)) {
                    String text = bundle.getString(MESSAGE);
                    if (text != null) {
                        mQueueManager.get().doToast(text);
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown message");
            }
        }
    }
}
