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
 * @author Philip Warner
 */
abstract public class ManagedTask extends Thread {

    private static final TaskSwitch mMessageSwitch = new TaskSwitch();
    /** The manager who we will use for progress etc, and who we will inform about our state. */
    @NonNull
    protected final TaskManager mManager;
    private final long mMessageSenderId;
    /** Options indicating the main onRun method has completed. Set in call do doFinish() in the UI thread. */
    private boolean mFinished = false;
    /** Indicates the user has requested a cancel. Up to subclass to decide what to do. Set by TaskManager. */
    private boolean mCancelFlg = false;

    /**
     * Constructor.
     *
     * @param manager Associated task manager
     */
    protected ManagedTask(@NonNull final TaskManager manager) {
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

        mMessageSenderId = mMessageSwitch.createSender(controller);
        // Save the stuff for later
        mManager = manager;
        // Add to my manager
        mManager.addTask(this);
    }

    @NonNull
    public static TaskSwitch getMessageSwitch() {
        return mMessageSwitch;
    }

    /**
     * Called when the task has finished, but *only* if the TaskManager has a context (ie. is
     * attached to an Activity). If the task manager is *not* attached to an activity, then onFinish()
     * will be called in the reconnect() call.
     *
     * The subclass must return 'true' if it was able to execute all required code in any required
     * TaskHandler. It does not matter if that code failed or succeeded, only that the TaskHandler
     * was executed (if necessary). TaskHandler objects will be cleared by the disconnect() call
     * and reset by the reconnect() call.
     */
    protected void onThreadFinish() {
        //do nothing
    }

    /** Called to do the main thread work. Can use doProgress() and showBriefMessage() to display messages. */
    abstract protected void onRun() throws InterruptedException;

    /**
     * Utility routine to ask the TaskManager to get a String from a resource ID.
     *
     * @param id Resource ID
     *
     * @return Result
     */
    @NonNull
    protected String getString(@StringRes final int id) {
        return mManager.getContext().getString(id);
    }

    /**
     * Utility to ask the TaskManager to update the ProgressDialog
     *
     * @param message Message to display
     * @param count   Counter. 0 if Max not set.
     */
    public void doProgress(@NonNull final String message, final int count) {
        mManager.doProgress(this, message, count);
    }

    /**
     * Utility to ask TaskManager to display a message
     *
     * @param message Message to display
     */
    protected void showQuickNotice(@NonNull final String message) {
        mManager.showQuickNotice(message);
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
            onRun();
        } catch (InterruptedException e) {
            mCancelFlg = true;
        } catch (Exception e) {
            Logger.error(e);
        }

        mFinished = true;
        // Let the implementation know it is finished
        onThreadFinish();

        // Queue the 'onTaskFinished' message; this should also inform the TaskManager
        mMessageSwitch.send(mMessageSenderId, new MessageSwitch.Message<TaskListener>() {
                    @Override
                    public boolean deliver(@NonNull final TaskListener listener) {
                        listener.onTaskFinished(ManagedTask.this);
                        return false;
                    }
                }
        );
    }

    /**
     * Mark this thread as 'cancelled'
     */
    protected void cancelTask() {
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
        void onTaskFinished(@NonNull final ManagedTask t);
    }

    /**
     * Controller interface for this object
     */
    public interface TaskController {
        void requestAbort();

        @NonNull
        ManagedTask getTask();
    }

    /**
     * STATIC Object for passing messages from background tasks to activities that may be recreated
     *
     * This object handles all underlying OnTaskEndedListener messages for every instance of this class.
     *
     * note to self: YES "public" is a MUST
     */
    @SuppressWarnings("WeakerAccess")
    public static class TaskSwitch extends MessageSwitch<TaskListener, TaskController> {
    }
}
