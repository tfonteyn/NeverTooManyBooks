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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.baseactivity.BaseActivityWithTasks;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

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
 * The controller gives access to the {@link ManagedTask}
 * or you can call {@link MessageSwitch.Controller#requestAbort()}
 * <p>
 * {@link ManagedTaskListener} can be implemented by other objects if they want to receive
 * {@link MessageSwitch.Message} of the task finishing via the
 * {@link ManagedTaskListener#onTaskFinished(ManagedTask)} ()}
 */
public abstract class ManagedTask
        extends Thread {

    /**
     * STATIC Object for passing messages from background tasks to activities
     * that may be recreated.
     * <p>
     * This object handles all underlying task messages for every instance of this class.
     */
    public static final MessageSwitch<ManagedTaskListener, ManagedTask>
            MESSAGE_SWITCH = new MessageSwitch<>();

    /** log tag. */
    private static final String TAG = "ManagedTask";
    /** The manager who we will use for progress etc, and who we will inform about our state. */
    @NonNull
    protected final TaskManager mTaskManager;

    /** identifier for this task. This is different from the internal thread id. */
    private final int mTaskId;


    private final long mMessageSenderId;
    /** message to send to the TaskManager when all is said and done. */
    @Nullable
    protected String mFinalMessage;
    /** Indicates the user has requested a cancel. Up to the subclass to decide what to do. */
    private boolean mCancelFlg;
    /** Controller instance (strong reference) for this specific ManagedTask. */
    @SuppressWarnings("FieldCanBeLocal")
    private final MessageSwitch.Controller<ManagedTask> mController =
            new MessageSwitch.Controller<ManagedTask>() {
                @Override
                public void requestAbort() {
                    cancelTask();
                }

                @NonNull
                @Override
                public ManagedTask get() {
                    return ManagedTask.this;
                }
            };

    /**
     * Constructor.
     * <p>
     * The task will be added to the task manager.
     *
     * @param taskManager Associated task manager
     * @param taskId      identifier
     * @param taskName    of this task(thread)
     */
    protected ManagedTask(@NonNull final TaskManager taskManager,
                          final int taskId,
                          @NonNull final String taskName) {

        mTaskId = taskId;

        // Set the thread name to something helpful.
        setName(taskName);

        mMessageSenderId = MESSAGE_SWITCH.createSender(mController);
        // Save the taskManager for later
        mTaskManager = taskManager;
        // Add myself to my manager
        mTaskManager.addTask(this);
    }

    /**
     * @return an identifier for this task.
     */
    public int getTaskId() {
        return mTaskId;
    }

    /**
     * Called to do the main thread work.
     */
    protected abstract void runTask()
            throws Exception;

    /**
     * Executed in main task thread.
     */
    @Override
    public void run() {
        try {
            runTask();
        } catch (@NonNull final InterruptedException e) {
            mCancelFlg = true;
        } catch (@NonNull final Exception e) {
            Logger.error(App.getAppContext(), TAG, e);
        }

        // Queue the 'onTaskFinished' message; this should also inform the TaskManager
        MESSAGE_SWITCH.send(mMessageSenderId, listener -> {
            listener.onTaskFinished(this);
            return false;
        });
    }

    /**
     * Mark this thread as 'cancelled'.
     */
    protected void cancelTask() {
        mCancelFlg = true;
        interrupt();
    }

    /**
     * Accessor to check if task cancelled.
     */
    public boolean isCancelled() {
        return mCancelFlg;
    }

    public long getSenderId() {
        return mMessageSenderId;
    }

    @Nullable
    public String getFinalMessage() {
        return mFinalMessage;
    }

    /**
     * Listener that lets other objects know about task progress and task completion.
     * <p>
     * See {@link com.hardbacknutter.nevertoomanybooks.searches.SearchCoordinator} for an example.
     */
    public interface ManagedTaskListener {

        /**
         * A task is finished.
         *
         * @param task the task
         */
        void onTaskFinished(@NonNull ManagedTask task);

        /**
         * Notification of progress.
         *
         * @param absPosition the new value to set
         * @param max         the (potentially) new estimate maximum value
         * @param message     to display. Set to "" to close the ProgressDialog
         */
        default void onTaskProgress(int absPosition,
                                    int max,
                                    @NonNull String message) {
            // ignore
        }

        /**
         * Notification of an interactive message.
         *
         * @param message to display to the user
         */
        default void onTaskUserMessage(@NonNull String message) {
            // ignore
        }
    }
}
