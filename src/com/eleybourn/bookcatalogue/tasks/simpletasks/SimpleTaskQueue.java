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

package com.eleybourn.bookcatalogue.tasks.simpletasks;

import android.os.Handler;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * As a reminder: https://developer.android.com/reference/android/os/AsyncTask
 * AsyncTasks are executed on a single thread to avoid common application errors
 * caused by parallel execution. If you truly want parallel execution, you can invoke
 * executeOnExecutor(java.util.concurrent.Executor, Object[]) with THREAD_POOL_EXECUTOR.
 * <p>
 * In short: AsyncTask is *not* a replacement for the way tasks are implemented/used
 * in this application.
 * <p>
 * On the other hand, using java.util.concurrent *would* be a standard replacement.
 * -----------------------------------------------------------------------------------------------
 * <p>
 * Class to perform time consuming but light-weight tasks in a worker thread. Users of this
 * class should implement their tasks as self-contained objects that implement {@link SimpleTask}.
 * <p>
 * The tasks run from (currently) a LIFO queue in a single thread; the run() method is called
 * in the alternate thread and the finished() method is called in the UI thread.
 * <p>
 * The execution queue is (currently) a FIFO deque so that the most recent queued is loaded.
 * This is good for loading (e.g.) gallery images to make sure that the most recently
 * viewed is loaded.
 * <p>
 * The results queue is executed in FIFO order.
 * <p>
 * In the future, both queues could be done independently and this object could be broken into
 * 3 classes: SimpleTaskQueueBase, SimpleTaskQueueFIFO and SimpleTaskQueueLIFO.
 * For now, this is not needed.
 * <p>
 * TODO: Consider an 'AbortListener' interface so tasks can be told when queue is aborted
 * TODO: Consider an 'aborted' flag to onFinish() and always calling onFinish() when queue is killed
 * <p>
 * NOTE: Tasks can call context.isTerminating() if necessary
 *
 * @author Philip Warner
 */
public class SimpleTaskQueue {

    private static final int MAX_TASKS = 10;
    private static final int MAX_TASKS_DEFAULT = 5;

    /** Execution queue. */
    private final BlockingDeque<SimpleTaskWrapper> mExecutionStack = new LinkedBlockingDeque<>();
    /** Results queue. */
    private final BlockingQueue<SimpleTaskWrapper> mResultQueue = new LinkedBlockingQueue<>();
    /** Handler for sending tasks to the UI thread. */
    private final Handler mHandler = new Handler();
    /** Name for this queue. */
    @NonNull
    private final String mName;
    /** Threads associate with this queue. */
    private final List<SimpleTaskQueueThread> mThreads = new ArrayList<>();
    /** Max number of threads to create. */
    private final int mMaxTasks;
    /** Flag indicating this object should terminate. */
    private boolean mTerminate;
    /** Number of currently queued, executing (or starting/finishing) tasks. */
    private int mManagedTaskCount;

    @Nullable
    private OnTaskStartListener mTaskStartListener;
    @Nullable
    private OnTaskFinishListener mTaskFinishListener;
    /**
     * Flag indicating runnable is queued but not run.
     * Avoids multiple unnecessary Runnable's
     */
    private boolean mDoProcessResultsIsQueued;
    /**
     * Method to ensure results queue is processed in the UI thread.
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
     * Constructor.
     *
     * @param name queue name
     */
    public SimpleTaskQueue(@NonNull final String name) {
        mName = name;
        mMaxTasks = MAX_TASKS_DEFAULT;
    }

    /**
     * Constructor.
     *
     * @param name     queue name
     * @param maxTasks the maximum queue length, must be between 1 and 10.
     */
    public SimpleTaskQueue(@NonNull final String name,
                           @IntRange(from = 1, to = MAX_TASKS) final int maxTasks) {
        mName = name;
        mMaxTasks = maxTasks;
        if (maxTasks < 1 || maxTasks > MAX_TASKS) {
            throw new IllegalArgumentException("maxTasks=" + maxTasks);
        }
    }

    /**
     * @return Flag indicating queue is terminating (finish() was called).
     */
    private boolean isTerminating() {
        return mTerminate;
    }

    /**
     * Terminate processing.
     */
    public void terminate() {
        synchronized (this) {
            mTerminate = true;
            for (Thread t : mThreads) {
                try {
                    t.interrupt();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    /**
     * Check to see if any tasks are active -- either queued, or with ending results.
     *
     * @return <tt>true</tt> if there are active tasks
     */
    public boolean hasActiveTasks() {
        synchronized (this) {
            return mManagedTaskCount > 0;
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

        if (DEBUG_SWITCHES.SIMPLE_TASKS && BuildConfig.DEBUG) {
            Logger.info(this, "ExecutionStack size=" + mExecutionStack.size()
                    + "|SimpleTask queued: " + task.toString());
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
     * Remove a previously requested task based on ID, if present.
     */
    public boolean remove(final long id) {
        for (SimpleTaskWrapper taskWrapper : mExecutionStack) {
            if (taskWrapper.getId() == id) {
                synchronized (this) {
                    if (mExecutionStack.remove(taskWrapper)) {
                        mManagedTaskCount--;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Remove a previously requested task, if present.
     */
    public void remove(@NonNull final SimpleTask task) {
        for (SimpleTaskWrapper taskWrapper : mExecutionStack) {
            if (taskWrapper.getTask().equals(task)) {
                synchronized (this) {
                    if (mExecutionStack.remove(taskWrapper)) {
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
    private void startTask(@NonNull final SimpleTaskQueueThread activeThread,
                           @NonNull final SimpleTaskWrapper taskWrapper) {
        final SimpleTask task = taskWrapper.getTask();

        if (mTaskStartListener != null) {
            mTaskStartListener.onTaskStart(task);
        }

        // Make the thread object available to provide Context related stuff (db's)
        taskWrapper.setActiveThread(activeThread);
        try {
            // kick of the actual task.
            task.run(taskWrapper);
            if (DEBUG_SWITCHES.SIMPLE_TASKS && BuildConfig.DEBUG) {
                Logger.info(this, "starting SimpleTask: " + task.toString());
            }

        } catch (Exception e) {
            taskWrapper.mException = e;
            Logger.error(e, "Error running task");
        } finally {
            // Dereference
            taskWrapper.setActiveThread(null);
        }

        synchronized (this) {
            // Queue the call to finished() if necessary.
            if (taskWrapper.isFinishRequested() || mTaskFinishListener != null) {
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
     * Runs in the UI thread, process the results queue.
     */
    private void processResults() {
        try {
            while (!mTerminate) {
                // Get next; if none, exit.
                SimpleTaskWrapper req = mResultQueue.poll();
                if (req == null) {
                    break;
                }

                final SimpleTask task = req.getTask();

                // Decrement the managed task count BEFORE we call any methods. This allows them
                // to call hasActiveTasks() and get a useful result when they are the last task.
                synchronized (this) {
                    mManagedTaskCount--;
                }

                // Call the task finish handler; log but ignore errors.
                if (req.isFinishRequested()) {
                    try {
                        task.onFinish(req.getException());
                    } catch (RuntimeException e) {
                        Logger.error(e, "Error processing request result");
                    }
                }

                // Call the task listener; tell it this task is done; log but ignore errors.
                if (mTaskFinishListener != null) {
                    try {
                        mTaskFinishListener.onTaskFinish(task, req.getException());
                    } catch (RuntimeException e) {
                        Logger.error(e,
                                     "Error from listener while processing request result");
                    }
                }
            }
        } catch (RuntimeException e) {
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
     * Interface for an object to listen for when tasks start.
     */
    public interface OnTaskStartListener {

        void onTaskStart(@NonNull SimpleTask task);
    }

    /**
     * Interface for an object to listen for when tasks finish.
     */
    public interface OnTaskFinishListener {

        void onTaskFinish(@NonNull SimpleTask task,
                          @Nullable Exception e);
    }

    /**
     * SimpleTask interface.
     * <p>
     * run() is called in worker thread
     * onFinish() is called in UI thread.
     */
    public interface SimpleTask {

        /**
         * Method called in queue thread to perform the background task.
         */
        void run(@NonNull SimpleTaskContext taskContext)
                throws Exception;

        /**
         * Method called in UI thread after the background task has finished.
         *
         * @param e the exception (if any) thrown in the run()
         */
        void onFinish(@Nullable Exception e);
    }

    public interface SimpleTaskContext {

        @NonNull
        DBA getDb();

        @NonNull
        CoversDBA getCoversDb();

        void setRequiresFinish(boolean requiresFinish);

        boolean isTerminating();
    }

    /**
     * Class to wrap a simpleTask with info needed by the queue.
     */
    private static class SimpleTaskWrapper
            implements SimpleTaskContext {

        @NonNull
        private static final AtomicInteger ID_COUNTER = new AtomicInteger();

        private static final String ILLEGAL_USE_ERROR =
                "SimpleTaskWrapper can only be used in a context during the run() stage";

        @NonNull
        private final SimpleTask mTask;
        private final long id;
        @NonNull
        private final SimpleTaskQueue mOwner;
        private Exception mException;

        private boolean mFinishRequested = true;

        @Nullable
        private SimpleTaskQueueThread mActiveThread;

        SimpleTaskWrapper(@NonNull final SimpleTaskQueue owner,
                          @NonNull final SimpleTask task) {
            mOwner = owner;
            mTask = task;
            id = ID_COUNTER.incrementAndGet();
        }

        private long getId() {
            return id;
        }

        @NonNull
        private SimpleTask getTask() {
            return mTask;
        }

        private void setActiveThread(@Nullable final SimpleTaskQueueThread activeThread) {
            mActiveThread = activeThread;
        }

        private boolean isFinishRequested() {
            return mFinishRequested;
        }

        private Exception getException() {
            return mException;
        }

        /**
         * Accessor when behaving as a context.
         * <p>
         * Do not close the database!
         *
         * @return a {@link DBA} which it gets from {@link SimpleTaskQueueThread}
         */
        @NonNull
        @Override
        public DBA getDb() {
            Objects.requireNonNull(mActiveThread, ILLEGAL_USE_ERROR);
            return mActiveThread.getDb();
        }

        /**
         * Accessor when behaving as a context.
         * <p>
         * Do not close the database!
         *
         * @return a {@link CoversDBA} which it gets from {@link SimpleTaskQueueThread}
         */
        @NonNull
        @Override
        public CoversDBA getCoversDb() {
            Objects.requireNonNull(mActiveThread, ILLEGAL_USE_ERROR);
            return mActiveThread.getCoversDb();
        }

        @Override
        public void setRequiresFinish(final boolean requiresFinish) {
            this.mFinishRequested = requiresFinish;
        }

        @Override
        public boolean isTerminating() {
            return mOwner.isTerminating();
        }
    }

    /**
     * Class to actually run the tasks. Can start more than one. They wait until there is
     * nothing left in the queue before terminating.
     * <p>
     * Databases are initialised with the application context.
     */
    private class SimpleTaskQueueThread
            extends Thread {

        private static final int TIMEOUT = 15000;
        /** DB Connection, if task requests one. Survives while thread is alive. */
        @Nullable
        private DBA mDb;
        @Nullable
        private CoversDBA mCoversDBAdapter;

        /**
         * Do not close the database; we close it for you when the task finishes.
         *
         * @return a database connection associated with this Task
         */
        @NonNull
        DBA getDb() {
            if (mDb == null) {
                // Reminder: don't make/put the context in a static variable! -> Memory Leak!
                mDb = new DBA(BookCatalogueApp.getAppContext());
            }
            return mDb;
        }

        /**
         * Do not close the database; we close it for you when the task finishes.
         *
         * @return a database connection associated with this Task
         */
        @SuppressWarnings("WeakerAccess")
        @NonNull
        public CoversDBA getCoversDb() {
            if (mCoversDBAdapter == null) {
                mCoversDBAdapter = CoversDBA.getInstance();
            }
            return mCoversDBAdapter;
        }

        /**
         * Main worker thread logic.
         */
        public void run() {
            try {
                // Set the thread name to something helpful.
                this.setName(mName);
                while (!mTerminate) {
                    SimpleTaskWrapper req = mExecutionStack.poll(TIMEOUT, TimeUnit.MILLISECONDS);

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

                    startTask(this, req);
                }
            } catch (InterruptedException ignore) {
            } catch (RuntimeException e) {
                Logger.error(e);
            } finally {
                if (mDb != null) {
                    mDb.close();
                }
                if (mCoversDBAdapter != null) {
                    mCoversDBAdapter.close();
                }
            }
        }
    }
}
