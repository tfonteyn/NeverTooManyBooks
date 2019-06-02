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
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.debug.Logger;

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
 * {@link ManagedTaskController}
 * Ask the {@link MessageSwitch} for the controller. The controller gives access to the
 * Sender (a {@link ManagedTask}) via its {@link ManagedTaskController#getManagedTask()} task
 * or can call {@link ManagedTaskController#requestAbort()}
 * <p>
 * {@link ManagedTaskListener} can be implemented by other objects if they want to receive
 * {@link MessageSwitch.Message} of the task finishing via the
 * {@link ManagedTaskListener#onTaskFinished(ManagedTask)} ()}
 *
 * @author Philip Warner
 */
public abstract class ManagedTask
        extends Thread {

    /**
     * STATIC Object for passing messages from background tasks to activities
     * that may be recreated.
     * <p>
     * This object handles all underlying task messages for every instance of this class.
     */
    public static final MessageSwitch<ManagedTaskListener, ManagedTaskController>
            MESSAGE_SWITCH = new MessageSwitch<>();

    /** The manager who we will use for progress etc, and who we will inform about our state. */
    @NonNull
    protected final TaskManager mTaskManager;
    /**
     *
     */
    private final long mMessageSenderId;
    /** message to send to the TaskManager when all is said and done. */
    @Nullable
    protected String mFinalMessage;
    /** Flag indicating the main runTask method has completed. Set in thread run. */
    private boolean mFinished;
    /** Indicates the user has requested a cancel. Up to the subclass to decide what to do. */
    private boolean mCancelFlg;

    /** Controller instance (strong reference) for this specific ManagedTask. */
    @SuppressWarnings("FieldCanBeLocal")
    private final ManagedTaskController mController = new ManagedTaskController() {
        @Override
        public void requestAbort() {
            cancelTask();
        }

        @NonNull
        @Override
        public ManagedTask getManagedTask() {
            return ManagedTask.this;
        }
    };

    /**
     * Constructor.
     *
     * @param taskManager Associated task manager
     * @param taskName    of this task(thread)
     */
    protected ManagedTask(@NonNull final TaskManager taskManager,
                          @NonNull final String taskName) {

        // Set the thread name to something helpful.
        setName(taskName);

        mMessageSenderId = MESSAGE_SWITCH.createSender(mController);
        // Save the taskManager for later
        mTaskManager = taskManager;
        // Add myself to my manager
        mTaskManager.addTask(this);
    }

    /**
     * Called when the task has finished, override if needed.
     */
    protected void onTaskFinish() {
    }

    /**
     * Called to do the main thread work.
     */
    protected abstract void runTask()
            throws Exception;


    @NonNull
    protected Resources getResources() {
        return mTaskManager.getContext().getResources();
    }

    @NonNull
    protected Context getContext() {
        return mTaskManager.getContext();
    }

    /**
     * Executed in main task thread.
     */
    @Override
    public void run() {
        try {
            runTask();
        } catch (InterruptedException e) {
            mCancelFlg = true;
        } catch (Exception e) {
            Logger.error(this, e);
        }

        mFinished = true;
        // Let the implementation know it is finished
        onTaskFinish();

        // Queue the 'onTaskFinished' message; this should also inform the TaskManager
        MESSAGE_SWITCH.send(mMessageSenderId, listener -> {
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
                                    Logger.debug(this, "run",
                                                 "ManagedTask=" + getName(),
                                                 "Delivering 'onTaskFinished' to: " + listener);
                                }
                                listener.onTaskFinished(this);
                                return false;
                            }
        );
    }

    /**
     * Mark this thread as 'cancelled'.
     */
    protected void cancelTask() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Logger.debug(this, "cancelTask");
        }
        mCancelFlg = true;
        interrupt();
    }

    /**
     * Accessor to check if task cancelled.
     */
    public boolean isCancelled() {
        return mCancelFlg;
    }

    /**
     * Accessor to check if task finished.
     */
    public boolean isFinished() {
        return mFinished;
    }

    public long getSenderId() {
        return mMessageSenderId;
    }

    @Nullable
    public String getFinalMessage() {
        return mFinalMessage;
    }
}
