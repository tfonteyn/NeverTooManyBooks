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
 * {@link MessageSwitch.Controller}
 * Ask the {@link MessageSwitch} for the controller.
 * The controller gives access to the {@link TaskManager}
 * or you can call {@link MessageSwitch.Controller#requestAbort()}
 * ------------------------------------------------------------------------------------------------
 * {@link ManagedTask.ManagedTaskListener} can be implemented by other objects for receiving
 * {@link TaskProgressMessage}, {@link TaskUserMessage} and {@link TaskFinishedMessage}
 */
public class TaskManager {

    /**
     * STATIC Object for passing messages from background tasks to activities
     * that may be recreated.
     * <p>
     * This object handles all underlying task messages for *every* instance of this class.
     */
    public static final MessageSwitch<ManagedTask.ManagedTaskListener, TaskManager>
            MESSAGE_SWITCH = new MessageSwitch<>();

    /** log tag. */
    private static final String TAG = "TaskManager";

    /** Unique identifier for this instance. */
    private final Long mId;

    /** List of ManagedTask being managed by *this* object. */
    private final List<TaskWrapper> mActiveTasks = new ArrayList<>();

    /**
     * Current progress message to display, even if no tasks running.
     * Setting to {@code null} or blank will remove the Progress Dialog if no tasks are left
     * running. If this is not done, the dialog WILL STAY OPEN
     */
    @Nullable
    private String mBaseMessage;

    @SuppressWarnings("FieldNotUsedInToString")
    private final ManagedTask.ManagedTaskListener mManagedTaskListener =
            new ManagedTask.ManagedTaskListener() {
                @Override
                public void onTaskFinished(@NonNull final ManagedTask task) {
                    // Remove the finished task from our list
                    synchronized (mActiveTasks) {
                        for (TaskWrapper taskWrapper : mActiveTasks) {
                            if (taskWrapper.task == task) {
                                mActiveTasks.remove(taskWrapper);
                                break;
                            }
                        }

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
                            Log.d(TAG, "onTaskFinished|finished=" + task.getName());
                            for (TaskWrapper taskWrapper : mActiveTasks) {
                                Log.d(TAG, "onTaskFinished|running=" + taskWrapper.task.getName());
                            }
                        }
                    }

                    // Tell all listeners that the task has finished.
                    MESSAGE_SWITCH.send(mId, new TaskFinishedMessage(task));

                    // Update the progress dialog
                    sendProgress();
                }
            };

    /** Indicates tasks are being cancelled. This is reset when a new task is added. */
    private boolean mIsCancelling;

    /**
     * MessageSwitch Controller instance (strong reference) for this object.
     */
    @SuppressWarnings({"FieldCanBeLocal", "FieldNotUsedInToString"})
    private final MessageSwitch.Controller<TaskManager> mController =
            new MessageSwitch.Controller<TaskManager>() {

                public void requestAbort() {
                    cancelActiveTasks();
                }

                @Override
                @NonNull
                public TaskManager get() {
                    return TaskManager.this;
                }
            };

    /** Indicates the TaskManager is terminating after the last task exits. */
    private boolean mIsTerminating;

    /**
     * Constructor.
     */
    public TaskManager() {
        mId = MESSAGE_SWITCH.createSender(mController);
    }

    @NonNull
    public Long getId() {
        return mId;
    }

    /**
     * Add a task to this object. Ignores duplicates if already present.
     *
     * @param task to add
     */
    void addTask(@NonNull final ManagedTask task) {
        // sanity check.
        if (mIsTerminating) {
            throw new IllegalStateException("Can not add a task when terminating");
        }

        mIsCancelling = false;
        synchronized (mActiveTasks) {
            if (getTaskWrapper(task) == null) {
                mActiveTasks.add(new TaskWrapper(task));
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
        MESSAGE_SWITCH.send(mId, new TaskUserMessage(message));
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
        TaskWrapper taskWrapper = getTaskWrapper(task);
        if (taskWrapper != null) {
            taskWrapper.progressMessage = message;
            taskWrapper.progressCurrent = count;
            sendProgress();
        }
    }

    /**
     * Set the maximum value for progress for the passed task.
     */
    public void setMaxProgress(@NonNull final ManagedTask task,
                               final int max) {
        TaskWrapper taskWrapper = getTaskWrapper(task);
        if (taskWrapper != null) {
            taskWrapper.progressMax = max;
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

            synchronized (mActiveTasks) {
                // Append each task message
                if (!mActiveTasks.isEmpty()) {
                    // if there was a baseMessage, add a linefeed to it.
                    if (progressMessage.length() > 0) {
                        progressMessage.append('\n');
                    }
                    progressMessage.append(Csv.join("\n", mActiveTasks, true,
                                                    "• ",
                                                    element -> element.progressMessage));
                }
            }

            // Sum the current & max values for each active task.
            // These will be our total values.
            int progressMax = 0;
            int progressCount = 0;
            synchronized (mActiveTasks) {
                for (TaskWrapper taskWrapper : mActiveTasks) {
                    progressMax += taskWrapper.progressMax;
                    progressCount += taskWrapper.progressCurrent;
                }
            }

            MESSAGE_SWITCH.send(mId, new TaskProgressMessage(progressCount, progressMax,
                                                             progressMessage.toString()));

        } catch (@NonNull final RuntimeException e) {
            Logger.error(App.getAppContext(), TAG, e, "Error updating progress");
        }
    }


    /**
     * Lookup the TaskWrapper for the passed task.
     *
     * @param task Task to lookup
     *
     * @return TaskWrapper associated with task.
     */
    @Nullable
    private TaskWrapper getTaskWrapper(@NonNull final ManagedTask task) {
        synchronized (mActiveTasks) {
            for (TaskWrapper taskWrapper : mActiveTasks) {
                if (taskWrapper.task == task) {
                    return taskWrapper;
                }
            }
        }
        return null;
    }

    /**
     * Cancel all tasks and stop listening.
     * Normally called when {@link BaseActivityWithTasks} itself is finishing.
     */
    public void terminate() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Log.d(TAG, "terminate");

        }
        // stop listening, used as sanity check in addTask.
        mIsTerminating = true;
        cancelActiveTasks();
    }

    /**
     * Cancel all tasks, but stay active and accept new tasks.
     */
    public void cancelActiveTasks() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Log.d(TAG, "cancelActiveTasks");

        }
        synchronized (mActiveTasks) {
            mIsCancelling = true;
            for (TaskWrapper taskWrapper : mActiveTasks) {
                taskWrapper.task.cancelTask();
            }
        }
    }

    public boolean isCancelling() {
        return mIsCancelling;
    }

    @Override
    @NonNull
    public String toString() {
        return "TaskManager{"
               + "mId=" + mId
               + ", mActiveTasks=" + mActiveTasks
               + ", mBaseMessage=`" + mBaseMessage + '`'
               + ", mIsCancelling=" + mIsCancelling
               + ", mIsTerminating=" + mIsTerminating
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
            implements Message<ManagedTask.ManagedTaskListener> {

        @NonNull
        private final ManagedTask mTask;

        TaskFinishedMessage(@NonNull final ManagedTask task) {
            mTask = task;
        }

        @Override
        public boolean deliver(@NonNull final ManagedTask.ManagedTaskListener listener) {
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
            implements Message<ManagedTask.ManagedTaskListener> {

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
        public boolean deliver(@NonNull final ManagedTask.ManagedTaskListener listener) {
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
            implements Message<ManagedTask.ManagedTaskListener> {

        @NonNull
        private final String mMessage;

        TaskUserMessage(@NonNull final String message) {
            mMessage = message;
        }

        @Override
        public boolean deliver(@NonNull final ManagedTask.ManagedTaskListener listener) {
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
     * Wraps a Task with progress info.
     */
    private static class TaskWrapper {

        @NonNull
        final ManagedTask task;
        @Nullable
        String progressMessage = "";
        int progressMax;
        int progressCurrent;

        TaskWrapper(@NonNull final ManagedTask task) {
            this.task = task;
        }
    }
}
