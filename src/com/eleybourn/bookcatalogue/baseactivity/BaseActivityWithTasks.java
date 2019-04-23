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

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.MessageSwitch;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager.TaskManagerController;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager.TaskManagerListener;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * TODO: Remove this! Fragments makes BaseActivityWithTasks mostly redundant.
 * <p>
 * Class used to manager a collection of background threads for a
 * {@link BaseActivityWithTasks} subclass.
 * <p>
 * Part of three components that make this easier:
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
 *
 * @author Philip Warner
 */
public abstract class BaseActivityWithTasks
        extends BaseActivity
        implements ProgressDialogFragment.OnProgressCancelledListener {

    private static final String TAG = BaseActivityWithTasks.class.getSimpleName();

    private static final String BKEY_TASK_MANAGER_ID = TAG + ":TaskManagerId";

    /** ID of associated TaskManager. */
    private long mTaskManagerId;
    /** Progress Dialog for this activity. */
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** Associated TaskManager. */
    @Nullable
    private TaskManager mTaskManager;
    /** Max value for Progress. */
    private int mProgressMax;
    /** Current value for Progress. */
    private int mProgressCount;
    /** Message for Progress. */
    @NonNull
    private String mProgressMessage = "";
    /**
     * Object to handle all TaskManager events.
     */
    @NonNull
    private final TaskManagerListener mTaskListener = new TaskManagerListener() {
        /**
         * @param taskManager TaskManager
         * @param task        task which is finishing.
         */
        @Override
        public void onTaskFinished(@NonNull final TaskManager taskManager,
                                   @NonNull final ManagedTask task) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
                Logger.debugEnter(BaseActivityWithTasks.this, "onTaskFinished",
                                  "task=`" + task.getName());
            }
            // Just pass this one on. This will allow sub classes to override the base method,
            // and as such get informed.
            BaseActivityWithTasks.this.onTaskFinished(task);
        }

        /**
         * Display a progress message
         *
         * @param count     the new value to set
         * @param max       the (potentially) new estimate maximum value
         * @param message   to display. Set to "" to close the ProgressDialog
         */
        @Override
        public void onProgress(final int count,
                               final int max,
                               @NonNull final String message) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
                String dbgMsg = "onProgress: " + count + '/' + max + ", '"
                        + message.replace("\n", "\\n") + '`';
                Logger.debugEnter(this, "onProgress", "msg=" + dbgMsg);
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
            // so, now using ||. Last progress msg might be lost though. See if we care ?

            // If empty, close any dialog
            if ((mProgressMessage.isEmpty()) || mProgressMax == mProgressCount) {
                closeProgressDialog();
            } else {
                updateProgressDialog();
            }
        }

        /**
         * Display an interactive message.
         */
        @Override
        public void onUserMessage(@NonNull final String message) {
            UserMessage.showUserMessage(BaseActivityWithTasks.this, message);
        }
    };

    /**
     * When the user clicks 'back/up', clean up any running tasks.
     */
    @Override
    public void onBackPressed() {
        cancelTasksAndUpdateProgress(true);
        super.onBackPressed();
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Restore mTaskManagerId if present
        if (savedInstanceState != null) {
            mTaskManagerId = savedInstanceState.getLong(BKEY_TASK_MANAGER_ID);
        }
    }

    /**
     * Get the task manager for this activity.
     * If we had a TaskManager before we took a nap, try to get it back using the mTaskManagerId
     * we saved in onSaveInstanceState.
     * Creates one if we don't yet have one.
     */
    @NonNull
    public TaskManager getTaskManager() {
        if (mTaskManager == null) {
            if (mTaskManagerId != 0) {
                TaskManagerController controller =
                        TaskManager.getMessageSwitch().getController(mTaskManagerId);
                if (controller != null) {
                    mTaskManager = controller.getTaskManager();
                } else {
                    Logger.warnWithStackTrace(this, "Have ID(" + mTaskManagerId + "),"
                            + " but can not find controller getting TaskManager");
                }
            }

            // Create if necessary
            if (mTaskManager == null) {
                TaskManager taskManager = new TaskManager(this);
                mTaskManagerId = taskManager.getId();
                mTaskManager = taskManager;
            }
        }
        return mTaskManager;
    }

    @Override
    @CallSuper
    protected void onPause() {
        // Stop listening
        if (mTaskManagerId != 0) {
            TaskManager.getMessageSwitch().removeListener(mTaskManagerId, mTaskListener);
            // If the Activity is finishing, tell the TaskManager to cancel all active
            // tasks and not to hasInternet new ones.
            if (isFinishing()) {
                getTaskManager().cancelAllTasksAndStopListening();
            }
        }
        closeProgressDialog();
        super.onPause();
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        // If we are finishing, we don't care about active tasks.
        if (!isFinishing()) {
            // Restore mTaskManager if present
            getTaskManager();
            TaskManager.getMessageSwitch().addListener(mTaskManagerId, mTaskListener, true);
        }
    }

    /**
     * Method to allow subclasses easy access to terminating tasks.
     *
     * @see TaskManagerListener#onTaskFinished
     */
    protected void onTaskFinished(@NonNull final ManagedTask task) {
        String msg = task.getFinalMessage();
        if (msg != null && !msg.isEmpty()) {
            UserMessage.showUserMessage(this, msg);
        }
    }

    /**
     * Update/init the ProgressDialog.
     * <li>If already showing but wrong type -> force a recreation
     * <li>If the task manager is cancelling, override any progress message with "Cancelling"
     * <li>If not showing, create/show with the current message/values.
     */
    private void updateProgressDialog() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Logger.debugEnter(this, "updateProgressDialog");
        }

        boolean wantInDeterminate = (mProgressMax == 0);

        // if currently shown, but no longer a suitable type due to a change of
        // mProgressMax, dismiss it.
        if (mProgressDialog != null) {
            if ((!wantInDeterminate && mProgressDialog.isIndeterminate())
                    || (wantInDeterminate && !mProgressDialog.isIndeterminate())) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }

        // If we are cancelling, override the message
        if (mTaskManager != null && mTaskManager.isCancelling()) {
            mProgressMessage = getString(R.string.progress_msg_cancelling);
        }

        // Create dialog if necessary
        if (mProgressDialog == null) {
            mProgressDialog = (ProgressDialogFragment) getSupportFragmentManager()
                    .findFragmentByTag(ProgressDialogFragment.TAG);
            if (mProgressDialog == null) {
                mProgressDialog = ProgressDialogFragment.newInstance(0, mProgressMessage,
                                                                     wantInDeterminate,
                                                                     mProgressCount, mProgressMax);
                // specific tags for specific tasks? -> NO, as the dialog is shared.
                mProgressDialog.show(getSupportFragmentManager(), ProgressDialogFragment.TAG);
            }
        } else {
            // otherwise just update it.
            getWindow().getDecorView().post(() -> {
                if (mProgressMax > 0) {
                    mProgressDialog.setMax(mProgressMax);
                    mProgressDialog.onProgress(mProgressCount);

                }
                mProgressDialog.onProgress(mProgressMessage);
            });
        }
    }

    /**
     * Dismiss the Progress Dialog, and null it so we can recreate when needed.
     */
    private void closeProgressDialog() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Logger.debugEnter(this, "closeProgressDialog");
        }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Called when the progress dialog was cancelled.
     *
     * @param taskId for the task; null if there was no embedded task.
     */
    @Override
    public void onProgressCancelled(@Nullable final Integer taskId) {
        cancelTasksAndUpdateProgress(false);
    }

    /**
     * Cancel all tasks, and if the progress is showing,
     * update it (it will check task manager status).
     *
     * @param forceShowProgress if {@code true} we'll force the progress dialog to show.
     */
    private void cancelTasksAndUpdateProgress(final boolean forceShowProgress) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.MANAGED_TASKS) {
            Logger.debugEnter(this, "cancelTasksAndUpdateProgress",
                              "showProgress=" + forceShowProgress);
        }
        if (mTaskManager != null) {
            mTaskManager.cancelAllTasks();
        }

        if ((mProgressDialog != null && mProgressDialog.isVisible()) || forceShowProgress) {
            updateProgressDialog();
        }
    }

    /**
     * Save the TaskManager ID for later retrieval.
     */
    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mTaskManagerId != 0) {
            outState.putLong(BaseActivityWithTasks.BKEY_TASK_MANAGER_ID, mTaskManagerId);
        }
    }
}
