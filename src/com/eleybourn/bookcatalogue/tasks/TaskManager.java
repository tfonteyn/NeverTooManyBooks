/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.baseactivity.ActivityWithTasks;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.messaging.MessageSwitch;
import com.eleybourn.bookcatalogue.messaging.MessageSwitch.Message;

import java.util.ArrayList;


/**
 * Class used to manager a collection of background threads for a
 * {@link ActivityWithTasks} subclass.
 *
 * Part of three components that make this easier:
 * - {@link TaskManager}
 * handles the management of multiple threads sharing a progressDialog
 *
 * - {@link ActivityWithTasks}
 * uses a TaskManager (and communicates with it) to handle progress messages for threads.
 * Deals with orientation changes in cooperation with TaskManager.
 *
 * - {@link ManagedTask}
 * Background task that is managed by TaskManager and uses TaskManager to do all display activities.
 *
 * @author Philip Warner
 */
public class TaskManager implements AutoCloseable {

    /**
     * STATIC Object for passing messages from background tasks to activities that may be recreated
     *
     * This object handles all underlying OnTaskEndedListener messages for every instance of this class.
     */
    private static final MessageSwitch<TaskManagerListener, TaskManagerController> mMessageSwitch = new MessageSwitch<>();

    private final Context mContext;

    /** List of tasks being managed by this object */
    private final ArrayList<TaskInfo> mTasks = new ArrayList<>();

    /** Current progress message to display, even if no tasks running.
     *  Setting to blank will remove the ProgressDialog
     */
    private String mBaseMessage = "";
    /** Flag indicating tasks are being cancelled. This is reset when a new task is added */
    private boolean mCancelling = false;
    /** Object for SENDING messages specific to this instance */
    private final Long mMessageSenderId = mMessageSwitch.createSender(new TaskManagerController() {
        @Override
        public void requestAbort() {
            TaskManager.this.cancelAllTasks();
        }

        @Override
        public TaskManager getManager() {
            return TaskManager.this;
        }
    });
    /** Flag indicating the TaskManager is terminating; will close after last task exits */
    private boolean mIsClosing = false;
    /* ====================================================================================================
     *  OnTaskManagerListener handling
     */
    /**
     * Listen for task messages, specifically, task termination
     */
    private final ManagedTask.TaskListener mTaskListener = new ManagedTask.TaskListener() {
        @Override
        public void onTaskFinished(@NonNull ManagedTask t) {
            TaskManager.this.onTaskFinished(t);
        }

    };

    /**
     * Constructor.
     */
    public TaskManager(Context context) {
        mContext = context;
    }

    public static MessageSwitch<TaskManagerListener, TaskManagerController> getMessageSwitch() {
        return mMessageSwitch;
    }

    public Long getSenderId() {
        return mMessageSenderId;
    }

    /* ====================================================================================================
     *  END OnTaskManagerListener handling
     */

    /**
     * Add a task to this object. Ignores duplicates if already present.
     *
     * @param t Task to add
     */
    public void addTask(@NonNull final ManagedTask t) {
        if (mIsClosing)
            throw new RuntimeException("Can not add a task when closing down");

        mCancelling = false;

        synchronized (mTasks) {
            if (getTaskInfo(t) == null) {
                mTasks.add(new TaskInfo(t));
                ManagedTask.getMessageSwitch().addListener(t.getSenderId(), mTaskListener, true);
            }
        }
    }

    /**
     * Accessor
     */
    public boolean isCancelling() {
        return mCancelling;
    }

    /**
     * Called when the onTaskFinished message is received by the listener object.
     */
    private void onTaskFinished(@NonNull final ManagedTask task) {
        boolean doClose;

        // Remove from the list of tasks. From now on, it should
        // not send any progress requests.
        synchronized (mTasks) {
            for (TaskInfo i : mTasks) {
                if (i.task == task) {
                    mTasks.remove(i);
                    break;
                }
            }
            doClose = (mIsClosing && mTasks.size() == 0);
        }

        // Tell all listeners that it has ended.
        mMessageSwitch.send(mMessageSenderId, new OnTaskEndedMessage(TaskManager.this, task));

        // Update the progress dialog
        updateProgressDialog();

        // Call close() if necessary
        if (doClose) {
            close();
        }
    }

    /**
     * Get the number of tasks currently managed.
     *
     * @return Number of tasks
     */
    public int count() {
        return mTasks.size();
    }

    /**
     * Utility routine to cancel all tasks.
     */
    public void cancelAllTasks() {
        synchronized (mTasks) {
            mCancelling = true;
            for (TaskInfo t : mTasks) {
                t.task.cancelTask();
            }
        }
    }

    /**
     * Update the base progress message. Used (generally) by the ActivityWithTasks to
     * display some text above the task info. Set to null to ensure ProgressDialog will
     * be removed.
     */
    public void doProgress(@Nullable final String message) {
        mBaseMessage = message;
        updateProgressDialog();
    }

    /**
     * Update the current ProgressDialog based on information about a task.
     *
     * @param task    The task associated with this message
     * @param message Message text
     * @param count   Counter for progress
     */
    public void doProgress(@NonNull final ManagedTask task, @Nullable final String message, final int count) {
        TaskInfo t = getTaskInfo(task);
        if (t != null) {
            t.progressMessage = message;
            t.progressCurrent = count;
            updateProgressDialog();
        }
    }

    /**
     * If in the UI thread, update the progress dialog, otherwise resubmit to UI thread.
     */
    private void updateProgressDialog() {
        try {
            // Start with the base message if present
            String mProgressMessage;
            if (mBaseMessage != null && mBaseMessage.length() > 0) {
                mProgressMessage = mBaseMessage;
            } else {
                mProgressMessage = "";
            }

            synchronized (mTasks) {
                // Append each task message
                if (mTasks.size() > 0) {
                    if (!mProgressMessage.isEmpty()) {
                        mProgressMessage += "\n";
                    }
                    if (mTasks.size() == 1) {
                        String oneMsg = mTasks.get(0).progressMessage;
                        if (oneMsg != null && !oneMsg.trim().isEmpty()) {
                            mProgressMessage += oneMsg;
                        }
                    } else {
                        final StringBuilder message = new StringBuilder();
                        boolean got = false;
                        // Don't append blank messages; allows tasks to hide.
                        for (TaskInfo taskInfo : mTasks) {
                            String oneMsg = taskInfo.progressMessage;
                            if (oneMsg != null && !oneMsg.trim().isEmpty()) {
                                if (got) {
                                    message.append("\n");
                                } else {
                                    got = true;
                                }
                                message.append(" - ").append(oneMsg);
                            }
                        }
                        if (message.length() > 0) {
                            mProgressMessage += message;
                        }
                    }
                }
            }

            // Sum the current & max values for each active task. This will be our new values.
            int progressMax = 0;
            int progressCount = 0;
            synchronized (mTasks) {
                for (TaskInfo taskInfo : mTasks) {
                    progressMax += taskInfo.progressMax;
                    progressCount += taskInfo.progressCurrent;
                }
            }

            // Now, display it if we have a context; if it is empty and complete, delete the progress.
            mMessageSwitch.send(mMessageSenderId, new OnProgressMessage(progressCount, progressMax, mProgressMessage));
        } catch (Exception e) {
            Logger.logError(e, "Error updating progress");
        }
    }

    /**
     * Make a toast message for the caller. Queue in UI thread if necessary.
     *
     * @param message Message to send
     */
    public void doToast(@NonNull final String message) {
        mMessageSwitch.send(mMessageSenderId, new OnToastMessage(message));
    }

    /**
     * Lookup the TaskInfo for the passed task.
     *
     * @param task Task to lookup
     *
     * @return TaskInfo associated with task.
     */
    @Nullable
    private TaskInfo getTaskInfo(@NonNull final ManagedTask task) {
        synchronized (mTasks) {
            for (TaskInfo taskInfo : mTasks) {
                if (taskInfo.task == task) {
                    return taskInfo;
                }
            }
        }
        return null;
    }

    /**
     * Set the maximum value for progress for the passed task.
     */
    public void setMax(@NonNull final ManagedTask task, final int max) {
        TaskInfo taskInfo = getTaskInfo(task);
        if (taskInfo != null) {
            taskInfo.progressMax = max;
            updateProgressDialog();
        }
    }

    /*
     * Return the associated activity object.
     *
     * @return	The context
     */
    public Context getContext() {
        synchronized (this) {
            return mContext;
        }
    }

    /**
     * Set the count value for progress for the passed task.
     */
    public void setCount(@NonNull final ManagedTask task, final int count) {
        TaskInfo taskInfo = getTaskInfo(task);
        if (taskInfo != null) {
            taskInfo.progressCurrent = count;
            updateProgressDialog();
        }
    }

    /**
     * Cancel all tasks and close dialogs then cleanup; if no tasks running, just close dialogs and cleanup
     */
    @Override
    public void close() {
        if (DEBUG_SWITCHES.TASKMANAGER && BuildConfig.DEBUG) {
            System.out.println("DBG: Task Manager close requested");
        }

        mIsClosing = true;
        synchronized (mTasks) {
            for (TaskInfo taskInfo : mTasks) {
                taskInfo.task.cancelTask();
            }
        }
    }

    /**
     * Allows other objects to know when a task completed. See SearchManager for an example.
     *
     * @author Philip Warner
     */
    public interface TaskManagerListener {
        void onTaskEnded(@NonNull final TaskManager manager, @NonNull final ManagedTask task);

        void onProgress(final int count, final int max, @NonNull final String message);

        void onToast(@NonNull final String message);

        void onFinished();
    }

    public interface TaskManagerController {
        void requestAbort();

        TaskManager getManager();
    }

    public static class OnTaskEndedMessage implements Message<TaskManagerListener> {
        private final TaskManager mManager;
        private final ManagedTask mTask;

        OnTaskEndedMessage(@NonNull final TaskManager manager, @NonNull final ManagedTask task) {
            mManager = manager;
            mTask = task;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            listener.onTaskEnded(mManager, mTask);
            return false;
        }
    }

    public static class OnProgressMessage implements Message<TaskManagerListener> {
        private final int mCount;
        private final int mMax;
        private final String mMessage;

        OnProgressMessage(final int count, final int max, @NonNull final String message) {
            mCount = count;
            mMax = max;
            mMessage = message;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            listener.onProgress(mCount, mMax, mMessage);
            return false;
        }
    }

    public static class OnToastMessage implements Message<TaskManagerListener> {
        private final String mMessage;

        OnToastMessage(@NonNull final String message) {
            mMessage = message;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            listener.onToast(mMessage);
            return false;
        }
    }

    public static class OnFinishedMessage implements Message<TaskManagerListener> {
        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            listener.onFinished();
            return false;
        }
    }

    // Task info for each ManagedTask object
    private class TaskInfo {
        final ManagedTask task;
        String progressMessage;
        int progressMax;
        int progressCurrent;

        TaskInfo(@NonNull final ManagedTask t) {
            this(t, 0, 0, "");
        }

        @SuppressWarnings("SameParameterValue")
        TaskInfo(@NonNull final ManagedTask t, final int max, final int curr, @NonNull final String message) {
            task = t;
            progressMax = max;
            progressCurrent = curr;
            progressMessage = message;
        }
    }
}
