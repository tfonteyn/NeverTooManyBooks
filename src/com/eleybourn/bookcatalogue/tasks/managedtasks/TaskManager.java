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

package com.eleybourn.bookcatalogue.tasks.managedtasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.managedtasks.MessageSwitch.Message;

import java.util.ArrayList;
import java.util.List;

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
 *
 * @author Philip Warner
 */
public class TaskManager {

    /**
     * STATIC Object for passing messages from background tasks to activities
     * that may be recreated.
     * <p>
     * This object handles all underlying task messages for *every* instance of this class.
     */
    private static final MessageSwitch<TaskManagerListener, TaskManagerController>
            MESSAGE_SWITCH = new MessageSwitch<>();

    /**
     * Unique identifier for this instance.
     * <p>
     * Used as senderId for SENDING messages specific to this instance.
     */
    private final Long mMessageSenderId;

    /**
     * List of ManagedTask being managed by *this* object.
     */
    private final List<TaskInfo> mManagedTasks = new ArrayList<>();

    @NonNull
    private final Context mContext;

    /**
     * Current progress message to display, even if no tasks running.
     * Setting to null or blank will remove the ProgressDialog if no tasks are left running.
     */
    @Nullable
    private String mBaseMessage;
    /**
     * Listener for ManagedTask messages.
     */
    private final ManagedTask.ManagedTaskListener mManagedTaskListener =
            new ManagedTask.ManagedTaskListener() {
                @Override
                public void onTaskFinished(@NonNull final ManagedTask task) {
                    // Remove the finished task from our list
                    synchronized (mManagedTasks) {
                        for (TaskInfo i : mManagedTasks) {
                            if (i.task == task) {
                                mManagedTasks.remove(i);
                                break;
                            }
                        }

                        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
                            for (TaskInfo i : mManagedTasks) {
                                Logger.info(
                                        TaskManager.this,
                                        "|Task `" + i.task.getName() + "` still running");
                            }
                        }
                    }

                    // Tell all listeners that the task has finished.
                    MESSAGE_SWITCH.send(mMessageSenderId,
                                        new TaskFinishedMessage(TaskManager.this, task));

                    // Update the progress dialog
                    sendTaskProgressMessage();
                }
            };

    /**
     * Indicates tasks are being cancelled. This is reset when a new task is added.
     */
    private boolean mCancelling;

    /**
     * Indicates the TaskManager is terminating; will close after last task exits.
     */
    private boolean mIsClosing;

    /**
     * Constructor.
     */
    public TaskManager(@NonNull final Context context) {

        /* Controller instance for this specific SearchManager */
        TaskManagerController controller = new TaskManagerController() {
            public void requestAbort() {
                TaskManager.this.cancelAllTasks();
            }

            @Override
            @NonNull
            public TaskManager getTaskManager() {
                return TaskManager.this;
            }
        };
        mMessageSenderId = MESSAGE_SWITCH.createSender(controller);

        mContext = context;
    }

    @NonNull
    public static MessageSwitch<TaskManagerListener, TaskManagerController> getMessageSwitch() {
        return MESSAGE_SWITCH;
    }

    /**
     * Return the associated activity object.
     *
     * @return The context
     */
    @NonNull
    public Context getContext() {
        synchronized (this) {
            return mContext;
        }
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
        synchronized (mManagedTasks) {
            if (getTaskInfo(task) == null) {
                mManagedTasks.add(new TaskInfo(task));
                // Tell the ManagedTask we are listening for messages.
                ManagedTask.getMessageSwitch().addListener(task.getSenderId(), mManagedTaskListener,
                                                           true);
            }
        }
    }

    public boolean isCancelling() {
        return mCancelling;
    }

    /**
     * Creates and send a {@link TaskProgressMessage} with the base/header message.
     * Used (generally) by {@link BaseActivityWithTasks} to display some text above
     * the task info. Set to null to ensure ProgressDialog will be removed.
     */
    public void sendHeaderTaskProgressMessage(@Nullable final String message) {
        mBaseMessage = message;
        sendTaskProgressMessage();
    }

    /**
     * Creates and send a {@link TaskProgressMessage} based on information about a task.
     *
     * @param task      The task associated with this message
     * @param messageId Message string id
     * @param count     Counter for progress
     */
    public void sendTaskProgressMessage(@NonNull final ManagedTask task,
                                        @StringRes final int messageId,
                                        final int count) {
        TaskInfo taskInfo = getTaskInfo(task);
        if (taskInfo != null) {
            taskInfo.progressMessage = messageId != 0 ? mContext.getString(messageId) : null;
            taskInfo.progressCurrent = count;
            sendTaskProgressMessage();
        }
    }

    /**
     * Creates and send a {@link TaskProgressMessage} based on information about a task.
     *
     * @param task    The task associated with this message
     * @param message Message text
     * @param count   Counter for progress
     */
    public void sendTaskProgressMessage(@NonNull final ManagedTask task,
                                        @Nullable final String message,
                                        final int count) {
        TaskInfo taskInfo = getTaskInfo(task);
        if (taskInfo != null) {
            taskInfo.progressMessage = message;
            taskInfo.progressCurrent = count;
            sendTaskProgressMessage();
        }
    }

    /**
     * Creates and send a {@link TaskProgressMessage} with the global/total progress of all tasks.
     */
    private void sendTaskProgressMessage() {
        try {
            // Start with the base message if present
            String progressMessage;
            if (mBaseMessage != null && !mBaseMessage.isEmpty()) {
                progressMessage = mBaseMessage;
            } else {
                progressMessage = "";
            }

            synchronized (mManagedTasks) {
                // Append each task message
                if (mManagedTasks.size() > 0) {
                    if (!progressMessage.isEmpty()) {
                        progressMessage += "\n";
                    }
                    if (mManagedTasks.size() == 1) {
                        String oneMsg = mManagedTasks.get(0).progressMessage;
                        if (oneMsg != null && !oneMsg.trim().isEmpty()) {
                            progressMessage += oneMsg;
                        }
                    } else {
                        final StringBuilder message = new StringBuilder();
                        boolean got = false;
                        // Don't append blank messages; allows tasks to hide.
                        for (TaskInfo taskInfo : mManagedTasks) {
                            String oneMsg = taskInfo.progressMessage;
                            if (oneMsg != null && !oneMsg.trim().isEmpty()) {
                                if (got) {
                                    message.append('\n');
                                } else {
                                    got = true;
                                }
                                message.append(" - ").append(oneMsg);
                            }
                        }
                        if (message.length() > 0) {
                            progressMessage += message;
                        }
                    }
                }
            }

            // Sum the current & max values for each active task.
            // These will be our total values.
            int progressMax = 0;
            int progressCount = 0;
            synchronized (mManagedTasks) {
                for (TaskInfo taskInfo : mManagedTasks) {
                    progressMax += taskInfo.progressMax;
                    progressCount += taskInfo.progressCurrent;
                }
            }

            MESSAGE_SWITCH.send(mMessageSenderId,
                                new TaskProgressMessage(progressCount, progressMax,
                                                        progressMessage));

        } catch (RuntimeException e) {
            Logger.error(e, "Error updating progress");
        }
    }

    /**
     * Creates and send a {@link TaskUserMessage}.
     *
     * @param message Message to send
     */
    public void sendTaskUserMessage(@NonNull final String message) {
        MESSAGE_SWITCH.send(mMessageSenderId, new TaskUserMessage(message));
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
        synchronized (mManagedTasks) {
            for (TaskInfo taskInfo : mManagedTasks) {
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
    public void setMaxProgress(@NonNull final ManagedTask task,
                               final int max) {
        TaskInfo taskInfo = getTaskInfo(task);
        if (taskInfo != null) {
            taskInfo.progressMax = max;
            sendTaskProgressMessage();
        }
    }

    /**
     * Cancel all tasks, but stay active and accept new tasks.
     */
    public void cancelAllTasks() {
        synchronized (mManagedTasks) {
            mCancelling = true;
            for (TaskInfo taskInfo : mManagedTasks) {
                taskInfo.task.cancelTask();
            }
        }
    }

    /**
     * Cancel all tasks and stop listening.
     * Normally called when {@link BaseActivityWithTasks} itself is finishing.
     */
    public void cancelAllTasksAndStopListening() {
        // stop listening, used as sanity check in addTask.
        mIsClosing = true;
        cancelAllTasks();
    }

    @Override
    @NonNull
    public String toString() {
        return "TaskManager{" +
                "mMessageSenderId=" + mMessageSenderId +
                ", mManagedTasks=" + mManagedTasks +
                ", mBaseMessage='" + mBaseMessage + '\'' +
                ", mManagedTaskListener=" + mManagedTaskListener +
                ", mCancelling=" + mCancelling +
                ", mIsClosing=" + mIsClosing +
                '}';
    }

    /**
     * Controller interface for this Object.
     */
    public interface TaskManagerController {

        void requestAbort();

        @NonNull
        TaskManager getTaskManager();
    }

    /**
     * Listener that lets other objects know about task progress and task completion.
     * <p>
     * See SearchManager for an example.
     *
     * @author Philip Warner
     */
    public interface TaskManagerListener {

        void onProgress(final int count,
                        final int max,
                        @NonNull final String message);

        void onUserMessage(@NonNull final String message);

        void onTaskFinished(@NonNull final TaskManager manager,
                            @NonNull final ManagedTask task);
    }

    public static class TaskFinishedMessage
            implements Message<TaskManagerListener> {

        @NonNull
        private final TaskManager mManager;
        @NonNull
        private final ManagedTask mTask;

        TaskFinishedMessage(@NonNull final TaskManager manager,
                            @NonNull final ManagedTask task) {
            mManager = manager;
            mTask = task;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
                Logger.info(this, "Delivering 'TaskFinishedMessage' to listener: " + listener +
                        "\n mTask=`" + mTask + '`');
            }
            listener.onTaskFinished(mManager, mTask);
            return false;
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskFinishedMessage{" +
                    "mManager=" + mManager +
                    ", mTask=" + mTask +
                    '}';
        }
    }

    public static class TaskProgressMessage
            implements Message<TaskManagerListener> {

        private final int mCount;
        private final int mMax;
        @NonNull
        private final String mMessage;

        TaskProgressMessage(final int count,
                            final int max,
                            @NonNull final String message) {
            mCount = count;
            mMax = max;
            mMessage = message;
        }

        @Override
        public boolean deliver(@NonNull final TaskManagerListener listener) {
            if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
                Logger.info(this, "Delivering 'TaskProgressMessage' to listener: " + listener +
                        "\n mMessage=`" + mMessage + '`');
            }
            listener.onProgress(mCount, mMax, mMessage);
            return false;
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskProgressMessage{" +
                    "mCount=" + mCount +
                    ", mMax=" + mMax +
                    ", mMessage='" + mMessage + '\'' +
                    '}';
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
            if (DEBUG_SWITCHES.MANAGED_TASKS && BuildConfig.DEBUG) {
                Logger.info(this, "Delivering 'TaskUserMessage' to listener: " + listener +
                        "\n mMessage=`" + mMessage + '`');
            }
            listener.onUserMessage(mMessage);
            return false;
        }

        @Override
        @NonNull
        public String toString() {
            return "TaskUserMessage{" +
                    "mMessage='" + mMessage + '\'' +
                    '}';
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
