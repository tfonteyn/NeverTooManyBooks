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
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.List;
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
    private final String mName;
    /** Threads associate with this queue */
    private final List<SimpleTaskQueueThread> mThreads = new ArrayList<>();
    /** Max number of threads to create */
    private final int mMaxTasks;
    /** Flag indicating this object should terminate. */
    private boolean mTerminate = false;
    /** Number of currently queued, executing (or starting/finishing) tasks */
    private int mManagedTaskCount = 0;

    private OnTaskStartListener mTaskStartListener = null;
    private OnTaskFinishListener mTaskFinishListener = null;
    /**
     * Flag indicating runnable is queued but not run; avoids multiple unnecessary Runnable's
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
        if (maxTasks < 1 || maxTasks > 10)
            throw new IllegalArgumentException("Illegal value for maxTasks");
    }

    /**
     * Accessor
     *
     * @return Flag indicating queue is terminating (finish() was called)
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
    public long enqueue(@NonNull final SimpleTask task) {
        SimpleTaskWrapper wrapper = new SimpleTaskWrapper(this, task);


        synchronized (this) {
            mExecutionStack.push(wrapper);
            mManagedTaskCount++;
        }

        //System.out.println("SimpleTaskQueue(added): " + mExecutionStack.size());
        synchronized (this) {
            int qSize = mExecutionStack.size();
            int nThreads = mThreads.size();
            if (nThreads < qSize && nThreads < mMaxTasks) {
                SimpleTaskQueueThread t = new SimpleTaskQueueThread();
                mThreads.add(t);
                t.start();
            }
        }
        return wrapper.id;
    }

    /**
     * Remove a previously requested task based on ID, if present
     */
    public boolean remove(final long id) {
        for (SimpleTaskWrapper w : mExecutionStack) {
            if (w.id == id) {
                synchronized (this) {
                    if (mExecutionStack.remove(w))
                        mManagedTaskCount--;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a previously requested task, if present
     */
    public boolean remove(@NonNull final SimpleTask task) {
        for (SimpleTaskWrapper w : mExecutionStack) {
            if (w.task.equals(task)) {
                synchronized (this) {
                    if (mExecutionStack.remove(w))
                        mManagedTaskCount--;
                }
                return true;
            }
        }
        return false;
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
            Logger.logError(e, "Error running task");
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
                if (req == null)
                    break;

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
                        Logger.logError(e, "Error processing request result");
                    }
                }

                // Call the task listener; log and ignore errors.
                if (mTaskFinishListener != null)
                    try {
                        mTaskFinishListener.onTaskFinish(task, req.exception);
                    } catch (Exception e) {
                        Logger.logError(e, "Error from listener while processing request result");
                    }
            }
        } catch (Exception e) {
            Logger.logError(e, "Exception in processResults in UI thread");
        }
    }

    /**
     * Accessor.
     */
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
         */
        void run(@NonNull final SimpleTaskContext taskContext);

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
        CatalogueDBAdapter getDb();

        void setRequiresFinish(final boolean requiresFinish);

        boolean isTerminating();
    }

    /**
     * Class to wrap a simpleTask with more info needed by the queue.
     *
     * @author Philip Warner
     */
    private static class SimpleTaskWrapper implements SimpleTaskContext {
        private static Long mCounter = 0L;
        public final SimpleTask task;
        public final long id;
        private final SimpleTaskQueue mOwner;
        public Exception exception;

        boolean finishRequested = true;
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
         */
        @Override
        public CatalogueDBAdapter getDb() {
            if (activeThread == null) {
                throw new IllegalStateException("SimpleTaskWrapper can only be used in a context during the run() stage");
            }
            return activeThread.getDb();
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
        CatalogueDBAdapter mDb = null;

        /**
         * Main worker thread logic
         */
        public void run() {
            try {
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

                    //System.out.println("SimpleTaskQueue(run): " + mQueue.size());
                    handleRequest(this, req);
                }
            } catch (Exception e) {
                Logger.logError(e);
            } finally {
                try {
                    if (mDb != null)
                        mDb.close();
                } catch (Exception ignored) {
                }
            }
        }

        /**
         * @return a database connection associated with this Task
         */
        public CatalogueDBAdapter getDb() {
            if (mDb == null) {
                // Reminder: don't make/put the context in a static variable! -> Memory Leak!
                mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
                mDb.open();
            }
            return mDb;
        }
    }
}
