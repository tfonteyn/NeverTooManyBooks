/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.tasks.managedtasks;

import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivityWithTasks;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.MessageSwitch.Message;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;

/**
 * Class used to manage background threads for a {@link BaseActivityWithTasks} subclass.
 * <p>
 * {@link ManagedTask}
 * Background task that is managed by TaskManager and uses TaskManager to coordinate
 * display activities.
 * <p>
 * {@link TaskManager}
 * handles the management of multiple tasks and passing messages with the help
 * of a {@link MessageSwitch}
 * <p>
 * {@link BaseActivityWithTasks}
 * Uses a TaskManager (and communicates with it) to handle messages for ManagedTask.
 * Deals with orientation changes in cooperation with TaskManager.
 * <p>
 * {@link MessageSwitch}
 * A Switchboard to receive and deliver {@link MessageSwitch.Message}.
 * ------------------------------------------------------------------------------------------------
 * <p>
 * {@link TaskManagerController}
 * Ask the {@link MessageSwitch} for the controller. The controller gives access to the
 * Sender (a {@link TaskManager}) via its {@link TaskManagerController#getTaskManager()} task
 * or can call {@link TaskManagerController#requestAbort()}
 * <p>
 * {@link TaskManagerListener} can be implemented by other objects for receiving
 * {@link TaskProgressMessage}, {@link TaskUserMessage} and {@link TaskFinishedMessage}
 */
public class TaskManager {

    /**
     * STATIC Object for passing messages from background tasks to activities
     * that may be recreated.
     * <p>
     * This object handles all underlying task messages for *every* instance of this class.
     */
    public static final MessageSwitch<TaskManagerListener, TaskManagerController>
            MESSAGE_SWITCH = new MessageSwitch<>();
    private static final String TAG = "TaskManager";
    /**
     * Unique identifier for this instance.
     * <p>
     * Used as senderId for SENDING messages specific to this instance.
     */
    private final Long mMessageSenderId;

    /**
     * List of ManagedTask being managed by *this* object.
     */
    private final List<TaskInfo> mTaskInfoList = new ArrayList<>();

    /**
     * Current progress message to display, even if no tasks running.
     * Setting to {@code null} or blank will remove the Progress Dialog if no tasks are left
     * running. If this is not done, the dialog WILL STAY OPEN
     */
    @Nullable
    private String mBaseMessage;
    @SuppressWarnings("FieldNotUsedInToString")
    private final ManagedTaskListener mManagedTaskListener = new ManagedTaskListener() {
        /**
         * Listener for ManagedTask messages.
         */
        @Override
        public void onTaskFinished(@NonNull final ManagedTask task) {
            // Remove the finished task from our list
            synchronized (mTaskInfoList) {
                for (TaskInfo i : mTaskInfoList) {
                    if (i.task == task) {
                        mTaskInfoList.remove(i);
                        break;
                    }
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
                    for (TaskInfo taskInfo : mTaskInfoList) {
                        Log.d(TAG, "onTaskFinished"
                                   + "|Task `" + taskInfo.task.getName() + "` still running");
                    }
                }
            }

            // Tell all listeners that the task has finished.
            MESSAGE_SWITCH.send(mMessageSenderId, new TaskFinishedMessage(task));

            // Update the progress dialog
            sendProgress();
        }

    };
    /**
     * Indicates tasks are being cancelled. This is reset when a new task is added.
     */
    private boolean mCancelling;
    /** Controller instance (strong reference) for this object. */
    @SuppressWarnings({"FieldCanBeLocal", "FieldNotUsedInToString"})
    private final TaskManagerController mController = new TaskManagerController() {

        public void requestAbort() {
            cancelAllTasks();
        }

        @Override
        @NonNull
        public TaskManager getTaskManager() {
            return TaskManager.this;
        }
    };
    /**
     * Indicates the TaskManager is terminating; will close after last task exits.
     */
    private boolean mIsClosing;

    /**
     * Constructor.
     *
     */
    public TaskManager() {
        mMessageSenderId = MESSAGE_SWITCH.createSender(mController);
    }

    @NonNull
    public Long getId() {
        return mMessageSenderId;
    }

    /**
     * Add a task to this object. Ignores duplicates if already present.
     *
     * @param task to add
     */
    public void addTask(@NonNull final ManagedTask task) {
        // sanity check.
        if (mIsClosing) {
            throw new IllegalStateException("Can not add a task when closing down");
        }

        mCancelling = false;
        synchronized (mTaskInfoList) {
            if (getTaskInfo(task) == null) {
                mTaskInfoList.add(new TaskInfo(task));
                // Tell the ManagedTask we are listening for messages.
                ManagedTask.MESSAGE_SWITCH.addListener(task.getSenderId(), true,
                                                       mManagedTaskListener);
            }
        }
    }

    /**
     * Creates and send a {@link TaskProgressMessage} with the base/header message.
     * Used (generally) by {@link BaseActivityWithTasks} to display some text above
     * the task info. Set to {@code null} to ensure Progress Dialog will be removed.
     */
    public void sendHeaderUpdate(@Nullable final String message) {
        mBaseMessage = message;
        sendProgress();
    }

    /**
     * Creates and send a {@link TaskUserMessage}.
     *
     * @param message to send
     */
    public void sendUserMessage(@NonNull final String message) {
        MESSAGE_SWITCH.send(mMessageSenderId, new TaskUserMessage(message));
    }

    /**
     * Creates and send a {@link TaskProgressMessage} based on information about a task.
     *
     * @param task    The task associated with this message
     * @param message Message string
     * @param count   Counter for progress
     */
    public void sendProgress(@NonNull final ManagedTask task,
                             @Nullable final String message,
                             final int count) {
        TaskInfo taskInfo = getTaskInfo(task);
        if (taskInfo != null) {
            taskInfo.progressMessage = message;
            taskInfo.progressCurrent = count;
            sendProgress();
        }
    }

    /**
     * Set the maximum value for progress for the passed task.
     */
    public void setMaxProgress(@NonNull final ManagedTask task,
                               final int max) {
        TaskInfo taskInfo = getTaskInfo(task);
        if (taskInfo != null) {
            taskInfo.progressMax = max;
            sendProgress();
        }
    }

    /**
     * Creates and send a {@link TaskProgressMessage} with the global/total progress of all tasks.
     */
    private void sendProgress() {
        try {
            // Start with the base message if we have one.
            StringBuilder progressMessage;
            if (mBaseMessage != null && !mBaseMessage.isEmpty()) {
                progressMessage = new StringBuilder(mBaseMessage);
            } else {
                progressMessage = new StringBuilder();
            }

            synchronized (mTaskInfoList) {
                // Append each task message
                if (!mTaskInfoList.isEmpty()) {
                    // if there was a baseMessage, add a linefeed to it.
                    if (progressMessage.length() > 0) {
                        progressMessage.append('\n');
                    }
                    progressMessage.append(Csv.join("\n", mTaskInfoList, true,
                                                    "• ",
                                                    element -> element.progressMessage));
                }
            }

            // Sum the current & max values for each active task.
            // These will be our total values.
            int progressMax = 0;
            int progressCount = 0;
            synchronized (mTaskInfoList) {
                for (TaskInfo taskInfo : mTaskInfoList) {
                    progressMax += taskInfo.progressMax;
                    progressCount += taskInfo.progressCurrent;
                }
            }

            MESSAGE_SWITCH.send(mMessageSenderId,
                                new TaskProgressMessage(progressCount, progressMax,
                                                        progressMessage.toString()));

        } catch (@NonNull final RuntimeException e) {
            Logger.error(App.getAppContext(), TAG, e, "Error updating progress");
        }
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
        synchronized (mTaskInfoList) {
            for (TaskInfo taskInfo : mTaskInfoList) {
                if (taskInfo.task == task) {
                    return taskInfo;
                }
            }
        }
        return null;
    }

    /**
     * Cancel all tasks and stop listening.
     * Normally called when {@link BaseActivityWithTasks} itself is finishing.
     */
    public void cancelAllTasksAndStopListening() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Log.d(TAG, "cancelAllTasksAndStopListening");

        }
        // stop listening, used as sanity check in addTask.
        mIsClosing = true;
        cancelAllTasks();
    }

    /**
     * Cancel all tasks, but stay active and accept new tasks.
     */
    public void cancelAllTasks() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Log.d(TAG, "cancelAllTasks");

        }
        synchronized (mTaskInfoList) {
            mCancelling = true;
            for (TaskInfo taskInfo : mTaskInfoList) {
                taskInfo.task.cancelTask();
            }
        }
    }

    public boolean isCancelling() {
        return mCancelling;
    }

    @Override
    @NonNull
    public String toString() {
        return "TaskManager{"
               + "mMessageSenderId=" + mMessageSenderId
               + ", mTaskInfoList=" + mTaskInfoList
               + ", mBaseMessage=`" + mBaseMessage + '`'
               + ", mCancelling=" + mCancelling
               + ", mIsClosing=" + mIsClosing
               + '}';
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @CallSuper
    @Override
    protected void finalize()
            throws Throwable {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Log.d(TAG, "finalize");
        }
        super.finalize();
    }

    public static class TaskFinishedMessage
            implements Message<TaskManagerListener> {

        @NonNull
        private final ManagedTask mTask;

        TaskFinishedMessage(@NonNull final ManagedTask task) {
            mTask = task;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            listener.onTaskFinished(mTask);
            return false;
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskFinishedMessage{"
                   + ", mTask=" + mTask
                   + '}';
        }
    }

    public static class TaskProgressMessage
            implements Message<TaskManagerListener> {

        private final int mPos;
        private final int mMax;
        @NonNull
        private final String mMessage;

        TaskProgressMessage(final int pos,
                            final int max,
                            @NonNull final String message) {
            mPos = pos;
            mMax = max;
            mMessage = message;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            listener.onTaskProgress(mPos, mMax, mMessage);
            return false;
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskProgressMessage{"
                   + "mPos=" + mPos
                   + ", mMax=" + mMax
                   + ", mMessage=`" + mMessage + '`'
                   + '}';
        }
    }

    public static class TaskUserMessage
            implements Message<TaskManagerListener> {

        @NonNull
        private final String mMessage;

        TaskUserMessage(@NonNull final String message) {
            mMessage = message;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            listener.onTaskUserMessage(mMessage);
            return false;
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskUserMessage{"
                   + "mMessage=`" + mMessage + '`'
                   + '}';
        }
    }

    /**
     * Task info for each ManagedTask object so we can keep track of progress.
     */
    private static class TaskInfo {

        @NonNull
        final ManagedTask task;
        @Nullable
        String progressMessage = "";
        int progressMax;
        int progressCurrent;

        TaskInfo(@NonNull final ManagedTask task) {
            this.task = task;
        }
    }
}
