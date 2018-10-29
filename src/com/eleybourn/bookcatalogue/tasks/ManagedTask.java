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

import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.messaging.MessageSwitch;

/**
 * Base class for handling tasks in background while displaying a ProgressDialog.
 *
 * Part of three components that make this easier:
 *
 * {@link TaskManager}
 * handles the management of multiple threads sharing a ProgressDialog
 *
 * {@link BaseActivityWithTasks}
 * Uses a TaskManager (and communicates with it) to handle progress messages for threads.
 * Deals with orientation changes in cooperation with TaskManager.
 *
 * {@link ManagedTask}
 * Background task that is managed by TaskManager and uses TaskManager to do all display activities.
 *
 *
 * {@link TaskController}
 * Ask the {@link TaskMessageSwitch} for the controller. The controller gives access to the sender.
 * via its {@link TaskController#getTask()} task and can {@link TaskController#requestAbort()}
 *
 * @author Philip Warner
 */
abstract public class ManagedTask extends Thread {

    private static final TaskMessageSwitch mMessageSwitch = new TaskMessageSwitch();
    /** The manager who we will use for progress etc, and who we will inform about our state. */
    @NonNull
    protected final TaskManager mTaskManager;
    /** */
    private final long mMessageSenderId;
    /** Options indicating the main runTask method has completed. Set in thread run */
    private boolean mFinished = false;
    /** Indicates the user has requested a onCancel. Up to the subclass to decide what to do. */
    private boolean mCancelFlg = false;

    /**
     * Constructor.
     *
     * @param name    of this task(thread)
     * @param manager Associated task manager
     */
    protected ManagedTask(@NonNull final String name, @NonNull final TaskManager manager) {
        /* Controller instance for this specific task */
        TaskController controller = new TaskController() {
            @Override
            public void requestAbort() {
                ManagedTask.this.cancelTask();
            }

            @NonNull
            @Override
            public ManagedTask getTask() {
                return ManagedTask.this;
            }
        };

        // Set the thread name to something helpful.
        setName(name);

        mMessageSenderId = mMessageSwitch.createSender(controller);
        // Save the stuff for later
        mTaskManager = manager;
        // Add to my manager
        mTaskManager.addTask(this);
    }

    @NonNull
    public static TaskMessageSwitch getMessageSwitch() {
        return mMessageSwitch;
    }

    /**
     * Called when the task has finished, override if needed.
     */
    protected void onTaskFinish() {
        //do nothing
    }

    /**
     * Called to do the main thread work.
     * Can use {@link #doProgress} and {@link #showUserMessage} to display messages.
     */
    abstract protected void runTask() throws InterruptedException;

    /**
     * Utility routine to ask the TaskManager to get a String from a resource ID.
     *
     * @param id Resource ID
     *
     * @return Result
     */
    @NonNull
    protected String getString(@StringRes final int id) {
        return mTaskManager.getContext().getString(id);
    }

    /**
     * Utility to ask the TaskManager to update the ProgressDialog
     *
     * @param message Message to display
     * @param count   Counter. 0 if Max not set.
     */
    public void doProgress(@NonNull final String message, final int count) {
        mTaskManager.doProgress(this, message, count);
    }

    /**
     * Utility to ask TaskManager to display a message
     *
     * @param message Message to display
     */
    protected void showUserMessage(@NonNull final String message) {
        mTaskManager.showUserMessage(message);
    }


    /* =====================================================================
     * Message Switchboard implementation
     * =====================================================================
     */

    /**
     * Executed in main task thread.
     */
    @Override
    public void run() {
        try {
            runTask();
        } catch (InterruptedException e) {
            Logger.info(ManagedTask.this, "" + this + " was interrupted");
            mCancelFlg = true;
        } catch (Exception e) {
            Logger.error(e);
        }

        mFinished = true;
        // Let the implementation know it is finished
        onTaskFinish();

        // Queue the 'onTaskFinished' message; this should also inform the TaskManager
        mMessageSwitch.send(mMessageSenderId,
                new MessageSwitch.Message<TaskListener>() {
                    @Override
                    public boolean deliver(@NonNull final TaskListener listener) {
                        listener.onTaskFinished(ManagedTask.this);
                        return false;
                    }

                    public String toString() {
                        return "Queue the 'onTaskFinished' message for sender id: " + mMessageSenderId;
                    }
                }
        );
    }

    /**
     * Mark this thread as 'cancelled'
     */
    protected void cancelTask() {
        if (DEBUG_SWITCHES.SEARCH_INTERNET && BuildConfig.DEBUG) {
            Logger.info(this, " cancelTask");
        }
        mCancelFlg = true;
        this.interrupt();
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

    /**
     * Allows other objects to know when a task completed.
     *
     * @author Philip Warner
     */
    public interface TaskListener {
        void onTaskFinished(@NonNull final ManagedTask task);
    }

    /**
     * Controller interface for this object
     */
    public interface TaskController {
        @SuppressWarnings("unused")
        void requestAbort();

        @NonNull
        ManagedTask getTask();
    }

    /**
     * STATIC Object for passing messages from background tasks to activities that may be recreated
     *
     * This object handles all underlying OnTaskEndedListener messages for every instance of this class.
     *
     * note to self: YES "public" is a MUST, lint is to eager
     */
    @SuppressWarnings("WeakerAccess")
    public static class TaskMessageSwitch extends MessageSwitch<TaskListener, TaskController> {
    }
}
