/*
 * @Copyright 2020 HardBackNutter
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

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;

import androidx.annotation.NonNull;

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

    /*
     * Queues we need.
     */
    /** main: long-running tasks, or tasks that can just wait. */
    public static final String Q_MAIN = "main";
    /** small_jobs: trivial background tasks that will only take a few seconds. */
    public static final String Q_SMALL_JOBS = "small_jobs";

//    /** For internal message sending. */
//    private static final String INTERNAL = "__internal";
//    /** Type of INTERNAL message. */
//    private static final String TOAST = "toast";
//    /** Key for actual (text) message. */
//    private static final String MESSAGE = "message";
    /**
     * Static reference to the active QueueManager - singleton.
     */
    private static QueueManager sInstance;

    /** Database access layer. */
    @NonNull
    private final QueueDAO mQueueDAO;
    /** Collection of currently active queues. */
    private final Map<String, Queue> mActiveQueues = new HashMap<>();
//    /** The UI thread. */
//    @NonNull
//    private final WeakReference<Thread> mUIThread;

    /** Objects listening for Event operations. */
    @NonNull
    private final List<WeakReference<OnChangeListener>> mEventChangeListeners = new ArrayList<>();
    /** Objects listening for Task operations. */
    @NonNull
    private final List<WeakReference<OnChangeListener>> mTaskChangeListeners = new ArrayList<>();

    /** Handle inter-thread messages. */
//    private final MessageHandler mMessageHandler;
    private final Handler mHandler = new Handler();

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

        // Save the thread ... it is the UI thread
//        mUIThread = new WeakReference<>(Thread.currentThread());
        // setup the handler with access to ourselves
//        mMessageHandler = new MessageHandler(new WeakReference<>(this));

        mQueueDAO = new QueueDAO(context);
        // Revive active queues.
        synchronized (this) {
            mQueueDAO.initAllQueues(this);
        }
        // Create the queues we need, if they do not already exist.
        mQueueDAO.createQueue(Q_MAIN);
        mQueueDAO.createQueue(Q_SMALL_JOBS);
    }

    /**
     * Constructor. Called from {@link com.hardbacknutter.nevertoomanybooks.StartupActivity}
     * so processing can start immediately after startup.
     *
     * @param context Current context; NOT stored.
     */
    public static void create(@NonNull final Context context) {
        if (sInstance == null) {
            sInstance = new QueueManager(context);
        }
    }

    public static QueueManager getQueueManager() {
        // do not lazy initialize here. We want the QueueManager running at startup.
        Objects.requireNonNull(sInstance, "init was not called?");
        return sInstance;
    }

    public void registerTaskListener(@NonNull final OnChangeListener listener) {
        synchronized (mTaskChangeListeners) {
            for (WeakReference<OnChangeListener> lr : mTaskChangeListeners) {
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
                for (WeakReference<OnChangeListener> l : mTaskChangeListeners) {
                    if (l.get().equals(listener)) {
                        ll.add(l);
                    }
                }
                for (WeakReference<OnChangeListener> l : ll) {
                    mTaskChangeListeners.remove(l);
                }
            }
        } catch (@NonNull final RuntimeException ignore) {
            // ignore
        }
    }

    public void registerEventListener(@NonNull final OnChangeListener listener) {
        synchronized (mEventChangeListeners) {
            for (WeakReference<OnChangeListener> lr : mEventChangeListeners) {
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
                for (WeakReference<OnChangeListener> l : mEventChangeListeners) {
                    if (l.get().equals(listener)) {
                        ll.add(l);
                    }
                }
                for (WeakReference<OnChangeListener> l : ll) {
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
        for (WeakReference<OnChangeListener> wl : list) {
            final OnChangeListener listener = wl.get();
            if (listener == null) {
                synchronized (mTaskChangeListeners) {
                    mTaskChangeListeners.remove(wl);
                }
            } else {
                try {
//                    mMessageHandler.post(listener::onChange);
                    mHandler.post(listener::onChange);
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore
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
        for (WeakReference<OnChangeListener> wl : list) {
            final OnChangeListener listener = wl.get();
            if (listener == null) {
                synchronized (mEventChangeListeners) {
                    mEventChangeListeners.remove(wl);
                }
            } else {
                try {
//                    mMessageHandler.post(listener::onChange);
                    mHandler.post(listener::onChange);
                } catch (@NonNull final RuntimeException ignore) {
                    // ignore
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

            if (mActiveQueues.containsKey(queueName)) {
                // notify the queue there is work to do
                final Queue queue = mActiveQueues.get(queueName);
                //noinspection ConstantConditions,SynchronizationOnLocalVariableOrMethodParameter
                synchronized (queue) {
                    // Reminder: notify() **MUST** be called inside
                    // a synchronized block on the object itself
                    queue.notify();
                }

            } else {
                // Create the queue; it will start and add itself to the manager
                new Queue(this, queueName);
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

//    /**
//     * Make a toast message for the caller. Queue in UI thread if necessary.
//     * <p>
//     * Hardwired to use {@link Toast} as there is no way to get an activity or view
//     * for using SnackBar.
//     */
//    private void doToast(@NonNull final String message) {
//        if (Thread.currentThread() == mUIThread.get()) {
//            synchronized (this) {
//                Toast.makeText(LocaleUtils.applyLocale(App.getAppContext()),
//                               message, Toast.LENGTH_LONG).show();
//            }
//        } else {
//            // Send message to the handler
//            final Message msg = mMessageHandler.obtainMessage();
//            final Bundle bundle = new Bundle();
//            bundle.putString(INTERNAL, TOAST);
//            bundle.putString(MESSAGE, message);
//            msg.setData(bundle);
//            mMessageHandler.sendMessage(msg);
//        }
//    }

    /**
     * Store an Event object for later retrieval after task has completed. This is
     * analogous to writing a line to the 'log file' for the task.
     *
     * @param taskId Related task
     * @param event  Event
     */
    public void storeTaskEvent(final long taskId,
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
    public void deleteTask(final long id) {
        boolean isActive = false;
        // Synchronize so that no queue will be able to get another task while we are deleting
        synchronized (this) {
            // Check if the task is running in a queue.
            for (Queue queue : mActiveQueues.values()) {
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
     * Delete Tasks (and its Events) more than 7 days old.
     */
    public void cleanupOldTasks() {
        mQueueDAO.cleanupOldTasks(7);
        notifyEventChange();
        notifyTaskChange();
    }

    /**
     * Delete Events more than 7 days old.
     */
    public void cleanupOldEvents() {
        mQueueDAO.cleanupOldEvents(7);
        notifyEventChange();
        notifyTaskChange();
    }

    public interface OnChangeListener {

        void onChange();
    }

//    /**
//     * Handler for internal UI thread messages.
//     */
//    private static class MessageHandler
//            extends Handler {
//
//        @NonNull
//        private final WeakReference<QueueManager> mQueueManager;
//
//        /**
//         * Constructor.
//         *
//         * @param queueManager the manager
//         */
//        MessageHandler(@NonNull final WeakReference<QueueManager> queueManager) {
//            mQueueManager = queueManager;
//        }
//
//        public void handleMessage(@NonNull final Message msg) {
//            final Bundle bundle = msg.getData();
//            if (bundle.containsKey(INTERNAL)) {
//                final String type = bundle.getString(INTERNAL);
//                if (TOAST.equals(type)) {
//                    final String text = bundle.getString(MESSAGE);
//                    if (text != null) {
//                        mQueueManager.get().doToast(text);
//                    }
//                }
//            } else {
//                throw new IllegalArgumentException("Unknown message");
//            }
//        }
//    }
}
