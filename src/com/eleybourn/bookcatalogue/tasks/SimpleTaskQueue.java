/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License V3
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.tasks;

import android.os.Handler;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Class to perform time consuming but light-weight tasks in a worker thread. Users of this
 * class should implement their tasks as self-contained objects that implement {@link SimpleTask}.
 *
 * The tasks run from (currently) a LIFO queue in a single thread; the run() method is called
 * in the alternate thread and the finished() method is called in the UI thread.
 *
 * The execution queue is (currently) a FIFO deque so that the most recent queued is loaded. This is
 * good for loading (eg) gallery images to make sure that the most recently viewed is loaded.
 *
 * The results queue is executed in FIFO order.
 *
 * In the future, both queues could be done independently and this object could be broken into
 * 3 classes: SimpleTaskQueueBase, SimpleTaskQueueFIFO and SimpleTaskQueueLIFO.
 * For now, this is not needed.
 *
 * TODO: Consider adding an 'AbortListener' interface so tasks can be told when queue is aborted
 * TODO: Consider adding an 'aborted' flag to onFinish() and always calling onFinish() when queue is killed
 *
 * NOTE: Tasks can call context.isTerminating() if necessary
 *
 * @author Philip Warner
 */
public class SimpleTaskQueue {
    /** Execution queue */
    private final BlockingDeque<SimpleTaskWrapper> mExecutionStack = new LinkedBlockingDeque<>();
    /** Results queue */
    private final BlockingQueue<SimpleTaskWrapper> mResultQueue = new LinkedBlockingQueue<>();
    /** Handler for sending tasks to the UI thread. */
    private final Handler mHandler = new Handler();
    /** Name for this queue */
    @NonNull
    private final String mName;
    /** Threads associate with this queue */
    private final List<SimpleTaskQueueThread> mThreads = new ArrayList<>();
    /** Max number of threads to create */
    private final int mMaxTasks;
    /** Options indicating this object should terminate. */
    private boolean mTerminate = false;
    /** Number of currently queued, executing (or starting/finishing) tasks */
    private int mManagedTaskCount = 0;

    @Nullable
    private OnTaskStartListener mTaskStartListener = null;
    @Nullable
    private OnTaskFinishListener mTaskFinishListener = null;
    /**
     * Options indicating runnable is queued but not run; avoids multiple unnecessary Runnable's
     */
    private boolean mDoProcessResultsIsQueued = false;
    /**
     * Method to ensure results queue is processed.
     */
    private final Runnable mDoProcessResults = new Runnable() {
        @Override
        public void run() {
            synchronized (mDoProcessResults) {
                mDoProcessResultsIsQueued = false;
            }
            processResults();
        }
    };

    /**
     * Constructor. Nothing to see here, move along. Just start the thread.
     *
     * @author Philip Warner
     */
    public SimpleTaskQueue(@NonNull final String name) {
        mName = name;
        mMaxTasks = 5;
    }

    /**
     * Constructor. Nothing to see here, move along. Just start the thread.
     *
     * @author Philip Warner
     */
    public SimpleTaskQueue(@NonNull final String name, @IntRange(from = 1, to = 10) final int maxTasks) {
        mName = name;
        mMaxTasks = maxTasks;
        if (maxTasks < 1 || maxTasks > 10) {
            throw new IllegalArgumentException("maxTasks=" + maxTasks);
        }
    }

    /**
     * Accessor
     *
     * @return Options indicating queue is terminating (finish() was called)
     */
    private boolean isTerminating() {
        return mTerminate;
    }

    /**
     * Terminate processing.
     */
    public void finish() {
        synchronized (this) {
            mTerminate = true;
            for (Thread t : mThreads) {
                try {
                    t.interrupt();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Check to see if any tasks are active -- either queued, or with ending results.
     */
    public boolean hasActiveTasks() {
        synchronized (this) {
            return (mManagedTaskCount > 0);
        }
    }

    /**
     * Queue a request to run in the worker thread.
     *
     * @param task Task to run.
     */
    public void enqueue(@NonNull final SimpleTask task) {
        SimpleTaskWrapper wrapper = new SimpleTaskWrapper(this, task);

        synchronized (this) {
            mExecutionStack.push(wrapper);
            mManagedTaskCount++;
        }

        if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
            Logger.info(this,"added: " + mExecutionStack.size());
        }
        synchronized (this) {
            int qSize = mExecutionStack.size();
            int nThreads = mThreads.size();
            if (nThreads < qSize && nThreads < mMaxTasks) {
                SimpleTaskQueueThread t = new SimpleTaskQueueThread();
                mThreads.add(t);
                t.start();
            }
        }
    }

    /**
     * Remove a previously requested task based on ID, if present
     */
    public boolean remove(final long id) {
        for (SimpleTaskWrapper w : mExecutionStack) {
            if (w.id == id) {
                synchronized (this) {
                    if (mExecutionStack.remove(w)) {
                        mManagedTaskCount--;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a previously requested task, if present
     */
    public void remove(@NonNull final SimpleTask task) {
        for (SimpleTaskWrapper w : mExecutionStack) {
            if (w.task.equals(task)) {
                synchronized (this) {
                    if (mExecutionStack.remove(w)) {
                        mManagedTaskCount--;
                    }
                }
                return;
            }
        }
    }

    /**
     * Run the task then queue the results.
     */
    private void handleRequest(@NonNull final SimpleTaskQueueThread thread,
                               @NonNull final SimpleTaskWrapper taskWrapper) {
        final SimpleTask task = taskWrapper.task;

        if (mTaskStartListener != null) {
            try {
                mTaskStartListener.onTaskStart(task);
            } catch (Exception ignore) {
            }
        }

        // Use the thread object to get some context stuff (mainly DBs)
        taskWrapper.activeThread = thread;
        try {
            task.run(taskWrapper);
        } catch (Exception e) {
            taskWrapper.exception = e;
            Logger.error(e, "Error running task");
        } finally {
            // Dereference
            taskWrapper.activeThread = null;
        }

        synchronized (this) {
            // Queue the call to finished() if necessary.
            if (taskWrapper.finishRequested || mTaskFinishListener != null) {
                try {
                    mResultQueue.put(taskWrapper);
                } catch (InterruptedException ignored) {
                }
                // Queue Runnable in the UI thread.
                synchronized (mDoProcessResults) {
                    if (!mDoProcessResultsIsQueued) {
                        mDoProcessResultsIsQueued = true;
                        mHandler.post(mDoProcessResults);
                    }
                }
            } else {
                // If no other methods are going to be called, then decrement
                // managed task count. We do not care about this task any more.
                mManagedTaskCount--;
            }
        }
    }

    /**
     * Run in the UI thread, process the results queue.
     */
    private void processResults() {
        try {
            while (!mTerminate) {
                // Get next; if none, exit.
                SimpleTaskWrapper req = mResultQueue.poll();
                if (req == null) {
                    break;
                }

                final SimpleTask task = req.task;

                // Decrement the managed task count BEFORE we call any methods. This allows them
                // to call hasActiveTasks() and get a useful result when they are the last task.
                synchronized (this) {
                    mManagedTaskCount--;
                }

                // Call the task handler; log and ignore errors.
                if (req.finishRequested) {
                    try {
                        task.onFinish(req.exception);
                    } catch (Exception e) {
                        Logger.error(e, "Error processing request result");
                    }
                }

                // Call the task listener; log and ignore errors.
                if (mTaskFinishListener != null) {
                    try {
                        mTaskFinishListener.onTaskFinish(task, req.exception);
                    } catch (Exception e) {
                        Logger.error(e, "Error from listener while processing request result");
                    }
                }
            }
        } catch (Exception e) {
            Logger.error(e, "Exception in processResults in UI thread");
        }
    }

    /**
     * Accessor.
     */
    @Nullable
    @SuppressWarnings("unused")
    public OnTaskStartListener getTaskStartListener() {
        return mTaskStartListener;
    }

    /**
     * Accessor.
     */
    @SuppressWarnings("unused")
    public void setTaskStartListener(@NonNull final OnTaskStartListener listener) {
        mTaskStartListener = listener;
    }

    /**
     * Accessor.
     */
    @Nullable
    @SuppressWarnings("unused")
    public OnTaskFinishListener getTaskFinishListener() {
        return mTaskFinishListener;
    }

    /**
     * Accessor.
     */
    public void setTaskFinishListener(@NonNull final OnTaskFinishListener listener) {
        mTaskFinishListener = listener;
    }

    /**
     * SimpleTask interface.
     *
     * run() is called in worker thread
     * finished() is called in UI thread.
     *
     * @author Philip Warner
     */
    public interface SimpleTask {
        /**
         * Method called in queue thread to perform the background task.
         *
         * 2018-10-14: added throws Exception to allow us to throw custom exceptions
         * Based on  {@link #handleRequest} where we have:
         * try {
         *             task.run(taskWrapper);
         *         } catch (Exception e) {
         *             taskWrapper.exception = e;
         *
         * Otherwise onFinish can not get our thrown exceptions
         *
         * The alternative was to change the argument to being a {@link SimpleTaskWrapper}
         * and access the {@link SimpleTaskWrapper#exception}
         */
        void run(@NonNull final SimpleTaskContext taskContext) throws Exception;

        /**
         * Method called in UI thread after the background task has finished.
         */
        void onFinish(@Nullable final Exception e);
    }

    /**
     * Interface for an object to listen for when tasks start.
     *
     * @author Philip Warner
     */
    public interface OnTaskStartListener {
        void onTaskStart(@NonNull final SimpleTask task);
    }

    /**
     * Interface for an object to listen for when tasks finish.
     *
     * @author Philip Warner
     */
    public interface OnTaskFinishListener {
        void onTaskFinish(@NonNull final SimpleTask task, @Nullable final Exception e);
    }

    public interface SimpleTaskContext {
        @NonNull
        CatalogueDBAdapter getOpenDb();

        void setRequiresFinish(final boolean requiresFinish);

        boolean isTerminating();
    }

    /**
     * Class to wrap a simpleTask with more info needed by the queue.
     *
     * @author Philip Warner
     */
    private static class SimpleTaskWrapper implements SimpleTaskContext {
        @NonNull
        private static Long mCounter = 0L;
        @NonNull
        public final SimpleTask task;
        public final long id;
        @NonNull
        private final SimpleTaskQueue mOwner;
        public Exception exception;

        boolean finishRequested = true;
        @Nullable
        SimpleTaskQueueThread activeThread = null;

        SimpleTaskWrapper(@NonNull final SimpleTaskQueue owner, @NonNull final SimpleTask task) {
            mOwner = owner;
            this.task = task;
            synchronized (mCounter) {
                this.id = ++mCounter;
            }
        }

        /**
         * Accessor when behaving as a context
         *
         * Do not close the database!
         *
         * Returns a {@link CatalogueDBAdapter} which it gets from the {@link SimpleTaskQueueThread}
         */
        @NonNull
        @Override
        public CatalogueDBAdapter getOpenDb() {
            Objects.requireNonNull(activeThread, "SimpleTaskWrapper can only be used in a context during the run() stage");
            return activeThread.getOpenDb();
        }

        @Override
        public void setRequiresFinish(final boolean requiresFinish) {
            this.finishRequested = requiresFinish;
        }

        @Override
        public boolean isTerminating() {
            return mOwner.isTerminating();
        }
    }

    /**
     * Class to actually run the tasks. Can start more than one. They wait until there is
     * nothing left in the queue before terminating.
     *
     * @author Philip Warner
     */
    private class SimpleTaskQueueThread extends Thread {
        /** DB Connection, if task requests one. Survives while thread is alive */
        @Nullable
        CatalogueDBAdapter mDb = null;

        /**
         * Do not close the database!
         *
         * @return a database connection associated with this Task
         */
        @NonNull
        public CatalogueDBAdapter getOpenDb() {
            if (mDb == null) {
                // Reminder: don't make/put the context in a static variable! -> Memory Leak!
                mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext())
                        .open();
            }
            return mDb;
        }

        /**
         * Main worker thread logic
         */
        public void run() {
            try {
                // Set the thread name to something helpful.
                this.setName(mName);
                while (!mTerminate) {
                    SimpleTaskWrapper req = mExecutionStack.poll(15000, TimeUnit.MILLISECONDS);

                    // If timeout occurred, get a lock on the queue and see if anything was queued
                    // in the intervening milliseconds. If not, delete this tread and exit.
                    if (req == null) {
                        synchronized (SimpleTaskQueue.this) {
                            req = mExecutionStack.poll();
                            if (req == null) {
                                mThreads.remove(this);
                                return;
                            }
                        }
                    }

                    if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
                        Logger.info(this,"run: " + mExecutionStack.size());
                    }
                    handleRequest(this, req);
                }
            } catch (InterruptedException ignore) {
            } catch (Exception e) {
                Logger.error(e);
            } finally {
                    if (mDb != null) {
                        mDb.close();
                    }
            }
        }
    }
}
