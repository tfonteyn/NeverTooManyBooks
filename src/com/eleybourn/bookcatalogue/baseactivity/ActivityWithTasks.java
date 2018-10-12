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
import android.content.DialogInterface.OnKeyListener;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.KeyEvent;

import com.eleybourn.bookcatalogue.BuildConfig;
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
 * TODO: Remove this!!!! Fragments makes ActivityWithTasks mostly redundant.
 *
 * Base class for handling tasks in background while displaying a {@link ProgressDialog}.
 *
 * Part of three components that make this easier:
 *
 * {@link TaskManager}
 * handles the management of multiple threads sharing a progressDialog
 *
 * {@link ActivityWithTasks}
 * Uses a TaskManager (and communicates with it) to handle progress messages for threads.
 * Deals with orientation changes in cooperation with TaskManager.
 *
 * {@link ManagedTask}
 * Background task that is managed by TaskManager and uses TaskManager to do all display activities.
 *
 * @author Philip Warner
 */
abstract public class ActivityWithTasks extends BookCatalogueActivity {
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
     * Wait for the 'Back' key and cancel all tasks on keyUp.
     */
    private final OnKeyListener mDialogKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(final DialogInterface dialog, final int keyCode, final KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                cancelAndUpdateProgress();
                return true;
            }
            return false;
        }
    };
    /**
     * Handler for the user cancelling the progress dialog.
     */
    private final OnCancelListener mCancelHandler = new OnCancelListener() {
        public void onCancel(DialogInterface i) {
            cancelAndUpdateProgress();
        }
    };

    /**
     * Object to handle all TaskManager events
     */
    @Nullable
    private final TaskManagerListener mTaskListener = new TaskManagerListener() {
        @Override
        public void onTaskEnded(@NonNull final TaskManager manager, @NonNull final ManagedTask task) {
            // Just pass this one on
            ActivityWithTasks.this.onTaskEnded(task);
        }

        @Override
        public void onProgress(final int count, final int max, @NonNull final String message) {
            if (BuildConfig.DEBUG) {
                String dbgMsg = count + "/" + max + ", '" + message.replace("\n", "\\n") + "'";
                Tracker.handleEvent(ActivityWithTasks.this, "SearchProgress " + dbgMsg, States.Running);
                Logger.info("PRG: " + dbgMsg);
            }

            // Save the details
            mProgressCount = count;
            mProgressMax = max;
            mProgressMessage = message.trim();

            // If empty, close any dialog
            if ((mProgressMessage.isEmpty()) && mProgressMax == mProgressCount) {
                closeProgressDialog();
            } else {
                updateProgress();
            }
        }

        /**
         * Display a Toast message
         */
        @Override
        public void onToast(@NonNull final String message) {
            StandardDialogs.showQuickNotice(ActivityWithTasks.this, message);
        }

        /**
         * TaskManager is finishing...cleanup.
         */
        @Override
        public void onFinished() {
            mTaskManager.close();
            mTaskManager = null;
            mTaskManagerId = 0;
        }
    };

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Restore mTaskManagerId if present
        if (savedInstanceState != null) {
            mTaskManagerId = savedInstanceState.getLong(BKEY_TASK_MANAGER_ID);
        }
    }

    /**
     * Utility routine to get the task manager for this activity
     */
    @Nullable
    protected TaskManager getTaskManager() {
        if (mTaskManager == null) {
            if (mTaskManagerId != 0) {
                TaskManagerController c = TaskManager.getMessageSwitch().getController(mTaskManagerId);
                if (c != null) {
                    mTaskManager = c.getManager();
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
    protected void onPause() {
        super.onPause();
        // Stop listening
        if (mTaskManagerId != 0) {
            TaskManager.getMessageSwitch().removeListener(mTaskManagerId, mTaskListener);
            // If it's finishing, the remove all tasks and cleanup
            if (isFinishing()) {
                TaskManager tm = getTaskManager();
                if (tm != null) {
                    tm.close();
                }
            }
        }
        closeProgressDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If we are finishing, we don't care about active tasks.
        if (!this.isFinishing()) {
            // Restore mTaskManager if present
            getTaskManager();

            // Listen
            TaskManager.getMessageSwitch().addListener(mTaskManagerId, mTaskListener, true);
        }
    }

    /**
     * Method to allow subclasses easy access to terminating tasks
     */
    protected void onTaskEnded(@NonNull final ManagedTask task) {
    }



    /**
     * Setup the ProgressDialog according to our needs
     */
    private void updateProgress() {
        boolean wantInDeterminate = (mProgressMax == 0);

        // if currently shown, but no longer suitable type due to a change of mProgressMax, dismiss it.
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
        if (mTaskManager.isCancelling()) {
            mProgressDialog.setMessage(getString(R.string.cancelling));
        } else {
            mProgressDialog.setMessage(mProgressMessage);
        }

        // Show it if necessary
        mProgressDialog.show();
    }

    private void createProgressDialog(final boolean wantInDeterminate) {
        mProgressDialog = new ProgressDialog(ActivityWithTasks.this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);

        mProgressDialog.setIndeterminate(wantInDeterminate);
        mProgressDialog.setProgressStyle(wantInDeterminate ? ProgressDialog.STYLE_SPINNER : ProgressDialog.STYLE_HORIZONTAL);

        mProgressDialog.setOnKeyListener(mDialogKeyListener);
        mProgressDialog.setOnCancelListener(mCancelHandler);
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Cancel all tasks, and if the progress is showing, update it (it will check task manager status)
     */
    private void cancelAndUpdateProgress() {
        if (mTaskManager != null) {
            mTaskManager.cancelAllTasks();
            closeProgressDialog();
        }

        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            updateProgress();
        }
    }

    /**
     * Save the TaskManager ID for later retrieval
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mTaskManagerId != 0) {
            outState.putLong(ActivityWithTasks.BKEY_TASK_MANAGER_ID, mTaskManagerId);
        }
    }
}
