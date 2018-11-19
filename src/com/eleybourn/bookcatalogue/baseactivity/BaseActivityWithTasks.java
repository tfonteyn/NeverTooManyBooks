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

package com.eleybourn.bookcatalogue.baseactivity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.debug.Tracker.States;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskManager;
import com.eleybourn.bookcatalogue.tasks.TaskManager.TaskManagerController;
import com.eleybourn.bookcatalogue.tasks.TaskManager.TaskManagerListener;

/**
 * TODO: Remove this!!!! Fragments makes BaseActivityWithTasks mostly redundant.
 *
 * Base class for handling tasks in background while displaying a {@link ProgressDialog}.
 *
 * Part of three components that make this easier:
 *
 * {@link TaskManager}
 * handles the management of multiple threads sharing a progressDialog
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
abstract public class BaseActivityWithTasks extends BaseActivity {
    private static final String BKEY_TASK_MANAGER_ID = "TaskManagerId";
    /** ID of associated TaskManager */
    private long mTaskManagerId = 0;
    /** ProgressDialog for this activity */
    @Nullable
    private ProgressDialog mProgressDialog = null;
    /** Associated TaskManager */
    @Nullable
    private TaskManager mTaskManager = null;
    /** Max value for ProgressDialog */
    private int mProgressMax = 0;
    /** Current value for ProgressDialog */
    private int mProgressCount = 0;
    /** Message for ProgressDialog */
    @NonNull
    private String mProgressMessage = "";

    /**
     * When the user clicks 'back/up':
     */
    @Override
    public void onBackPressed() {
        // clean up any running tasks.
        cancelAndUpdateProgress();
        super.onBackPressed();
    }

    /**
     * Object to handle all TaskManager events
     */
    @NonNull
    private final TaskManagerListener mTaskListener = new TaskManagerListener() {
        @Override
        public void onTaskEnded(final @NonNull TaskManager manager, final @NonNull ManagedTask task) {
            if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
                Logger.info(BaseActivityWithTasks.this, "passthrough onTaskEnded with task=" + task.getName());
            }
            // Just pass this one on
            BaseActivityWithTasks.this.onTaskEnded(task);
        }

        @Override
        public void onProgress(final int count, final int max, final @NonNull String message) {
            if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
                @SuppressWarnings("UnusedAssignment")
                String dbgMsg = "onProgress: " + count + "/" + max + ", '" + message.replace("\n", "\\n") + "'";

                Tracker.handleEvent(BaseActivityWithTasks.this, "SearchProgress " + dbgMsg, States.Running);
                Logger.info(BaseActivityWithTasks.this, dbgMsg);
            }

            // Save the details
            mProgressCount = count;
            mProgressMax = max;
            mProgressMessage = message.trim();

            // original code used &&
            //       if ((mProgressMessage.isEmpty()) && mProgressMax == mProgressCount) {
            //
            // but when updating a single book, I found timing issues where a 'real' message could
            // arrive with progress 1/1. And the dialog does not close.. and all is blocked.
            // so, now using ||. Last progress msg might be lost though. Important or not ?

            // If empty, close any dialog
            if ((mProgressMessage.isEmpty()) || mProgressMax == mProgressCount) {
                closeProgressDialog();
            } else {
                initProgressDialog();
            }
        }

        /**
         * Display a message
         */
        @Override
        public void onShowUserMessage(final @NonNull String message) {
            StandardDialogs.showUserMessage(BaseActivityWithTasks.this, message);
        }

        /**
         * TaskManager is finishing...cleanup.
         */
        @Override
        public void onFinished() {
            if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
                Logger.info(BaseActivityWithTasks.this, "TaskManagerListener `" + mTaskManagerId + "` finishing for activity: " +
                        BaseActivityWithTasks.this);
            }
            mTaskManager.close();
            mTaskManager = null;
            mTaskManagerId = 0;
        }
    };

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        super.onCreate(savedInstanceState);

        // Restore mTaskManagerId if present
        if (savedInstanceState != null) {
            mTaskManagerId = savedInstanceState.getLong(BKEY_TASK_MANAGER_ID);
        }
        Tracker.exitOnCreate(this);
    }

    /**
     * Utility routine to get the task manager for this activity
     */
    @NonNull
    protected TaskManager getTaskManager() {
        if (mTaskManager == null) {
            if (mTaskManagerId != 0) {
                TaskManagerController controller = TaskManager.getMessageSwitch().getController(mTaskManagerId);
                if (controller != null) {
                    mTaskManager = controller.getManager();
                } else {
                    Logger.error("Have ID("+mTaskManagerId+"), but can not find controller getting TaskManager");
                }
            } //else {
            //Logger.error("Task manager requested, but no ID available");
            //}

            // Create if necessary
            if (mTaskManager == null) {
                TaskManager tm = new TaskManager(this);
                mTaskManagerId = tm.getSenderId();
                mTaskManager = tm;
            }
        }
        return mTaskManager;
    }

    @Override
    @CallSuper
    protected void onPause() {
        Tracker.enterOnPause(this);
        // Stop listening
        if (mTaskManagerId != 0) {
            TaskManager.getMessageSwitch().removeListener(mTaskManagerId, mTaskListener);
            // If it's finishing, remove all tasks and cleanup
            if (isFinishing()) {
                getTaskManager().close();
            }
        }
        closeProgressDialog();
        super.onPause();
        Tracker.exitOnPause(this);
    }

    @Override
    @CallSuper
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        // If we are finishing, we don't care about active tasks.
        if (!isFinishing()) {
            // Restore mTaskManager if present
            getTaskManager();
            // Listen
            TaskManager.getMessageSwitch().addListener(mTaskManagerId, mTaskListener, true);
        }
        Tracker.exitOnResume(this);
    }

    /**
     * Method to allow subclasses easy access to terminating tasks
     *
     * @see TaskManagerListener#onTaskEnded
     */
    protected void onTaskEnded(final @NonNull ManagedTask task) {
    }

    /**
     * Setup the ProgressDialog according to our needs
     */
    private void initProgressDialog() {
        if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
            Logger.info(BaseActivityWithTasks.this, "initProgressDialog");
        }

        boolean wantInDeterminate = (mProgressMax == 0);

        // if currently shown, but no longer a suitable type due to a change of mProgressMax, dismiss it.
        if (mProgressDialog != null) {
            if ((!wantInDeterminate && mProgressDialog.isIndeterminate())
                    || (wantInDeterminate && !mProgressDialog.isIndeterminate())) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }

        // Create dialog if necessary
        if (mProgressDialog == null) {
            createProgressDialog(wantInDeterminate);
        }

        if (mProgressMax > 0) {
            mProgressDialog.setMax(mProgressMax);
        }
        mProgressDialog.setProgress(mProgressCount);

        // Set message; if we are cancelling we override the message
        if (mTaskManager != null && mTaskManager.isCancelling()) {
            mProgressDialog.setMessage(getString(R.string.progress_msg_cancelling));
        } else {
            mProgressDialog.setMessage(mProgressMessage);
        }

        // Show it if necessary
        mProgressDialog.show();
    }

    private void createProgressDialog(final boolean wantInDeterminate) {
        mProgressDialog = new ProgressDialog(BaseActivityWithTasks.this);
        mProgressDialog.setIndeterminate(wantInDeterminate);
        mProgressDialog.setProgressStyle(wantInDeterminate ? ProgressDialog.STYLE_SPINNER : ProgressDialog.STYLE_HORIZONTAL);

        mProgressDialog.setCanceledOnTouchOutside(false); // back button only
        mProgressDialog.setOnCancelListener( new OnCancelListener() {
            public void onCancel(DialogInterface i) {
                cancelAndUpdateProgress();
            }
        });
    }

    private void closeProgressDialog() {
        if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
            Logger.info(BaseActivityWithTasks.this, "closeProgressDialog");
        }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Cancel all tasks, and if the progress is showing, update it (it will check task manager status)
     */
    private void cancelAndUpdateProgress() {
        if (DEBUG_SWITCHES.MESSAGING && BuildConfig.DEBUG) {
            Logger.info(BaseActivityWithTasks.this, "cancelAndUpdateProgress");
        }
        if (mTaskManager != null) {
            mTaskManager.cancelAllTasks();
            closeProgressDialog();
        }

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            initProgressDialog();
        }
    }

    /**
     * Save the TaskManager ID for later retrieval
     */
    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mTaskManagerId != 0) {
            outState.putLong(BaseActivityWithTasks.BKEY_TASK_MANAGER_ID, mTaskManagerId);
        }
        super.onSaveInstanceState(outState);
    }
}
