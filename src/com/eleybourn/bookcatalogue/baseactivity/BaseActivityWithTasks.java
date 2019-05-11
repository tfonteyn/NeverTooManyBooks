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
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.managedtasks.MessageSwitch;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManager;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManagerController;
import com.eleybourn.bookcatalogue.tasks.managedtasks.TaskManagerListener;
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
        implements ProgressDialogFragment.OnUserCancelledListener,
                   TaskManagerListener {

    private static final String TAG = BaseActivityWithTasks.class.getSimpleName();

    private static final String BKEY_TASK_MANAGER_ID = TAG + ":TaskManagerId";

    /** ID of associated TaskManager. */
    private long mTaskManagerId;
    /** Associated TaskManager. */
    @Nullable
    private TaskManager mTaskManager;

    /** A cheaper progress dialog. */
    private View mProgressOverlayView;

    private TextView mProgressMessageView;

    private ProgressBar mProgressBar;

    /** Max value for Progress. */
    private int mProgressMax;
    /** Current value for Progress. */
    private int mProgressCount;
    /** Message for Progress. */
    @NonNull
    private String mProgressMessage = "";

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_with_legacy_tasks;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProgressOverlayView = findViewById(R.id.progressOverlay);
        Objects.requireNonNull(mProgressOverlayView);

        mProgressBar = findViewById(R.id.progressBar);
        mProgressMessageView = findViewById(R.id.progressMessage);

        // Restore mTaskManagerId if present
        if (savedInstanceState != null) {
            mTaskManagerId = savedInstanceState.getLong(BKEY_TASK_MANAGER_ID);
        }
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        // If we are finishing, we don't care about active tasks.
        if (!isFinishing()) {
            // Restore/create mTaskManager.
            getTaskManager();
            TaskManager.MESSAGE_SWITCH.addListener(mTaskManagerId, this, true);
        }
    }

    @Override
    @CallSuper
    protected void onPause() {
        // Stop listening
        if (mTaskManagerId != 0) {
            TaskManager.MESSAGE_SWITCH.removeListener(mTaskManagerId, this);
            // If the Activity is finishing, tell the TaskManager to cancel all active
            // tasks and reject new ones.
            if (isFinishing()) {
                getTaskManager().cancelAllTasksAndStopListening();
            }
        }
        closeProgressDialog();
        super.onPause();
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

    /**
     * When the user clicks 'back/up', clean up any running tasks.
     */
    @Override
    public void onBackPressed() {
        cancelTasksAndUpdateProgress(true);
        super.onBackPressed();
    }

    /**
     * Get the task manager for this activity.
     * If we had a TaskManager before we took a nap, try to get it back using the mTaskManagerId
     * we saved in onSaveInstanceState.
     * Creates one if we don't yet have one.
     *
     * @return the TaskManager
     */
    @NonNull
    public TaskManager getTaskManager() {
        if (mTaskManager == null) {
            if (mTaskManagerId != 0) {
                TaskManagerController controller =
                        TaskManager.MESSAGE_SWITCH.getController(mTaskManagerId);
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

    /**
     * @param taskManager TaskManager
     * @param task        task which is finishing.
     */
    @Override
    public void onTaskFinished(@NonNull final TaskManager taskManager,
                               @NonNull final ManagedTask task) {
        String msg = task.getFinalMessage();
        if (msg != null && !msg.isEmpty()) {
            UserMessage.showUserMessage(this, msg);
        }
    }

    /**
     * Display a progress message.
     *
     * @param absPosition the new value to set
     * @param max         the (potentially) new estimate maximum value
     * @param message     to display. Set to "" to close the ProgressDialog
     */
    @Override
    public void onTaskProgress(final int absPosition,
                               final int max,
                               @NonNull final String message) {
        // Save the details
        mProgressCount = absPosition;
        mProgressMax = max;
        mProgressMessage = message.trim();

        // If empty and we reached the end, close any dialog
        if ((mProgressMessage.isEmpty()) && max == absPosition) {
            closeProgressDialog();
        } else {
            runOnUiThread(this::updateProgressDialog);
        }
    }

    /**
     * Display an interactive message.
     */
    @Override
    public void onTaskUserMessage(@NonNull final String message) {
        UserMessage.showUserMessage(this, message);
    }

    /**
     * Update/init the ProgressDialog.
     * <ul>
     * <li>If already showing but wrong type -> force a recreation</li>
     * <li>If the task manager is cancelling, override any progress message with "Cancelling"</li>
     * <li>If not showing, create/show with the current message/values.</li>
     * </ul>
     */
    private void updateProgressDialog() {
        // If we are cancelling, override the message
        if (mTaskManager != null && mTaskManager.isCancelling()) {
            mProgressMessage = getString(R.string.progress_msg_cancelling);
        }

        boolean wantInDeterminate = mProgressMax == 0;

        // check if the required type has changed
        if (wantInDeterminate != mProgressBar.isIndeterminate()) {
            mProgressBar.setIndeterminate(wantInDeterminate);
        }

        if (mProgressMax > 0) {
            mProgressBar.setMax(mProgressMax);
            mProgressBar.setProgress(mProgressCount);
        }
        mProgressMessageView.setText(mProgressMessage);
        if (mProgressOverlayView.getVisibility() != View.VISIBLE) {
            mProgressOverlayView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Dismiss the Progress Dialog, and {@code null} it so we can recreate when needed.
     */
    private void closeProgressDialog() {
        if (mProgressOverlayView != null) {
            mProgressOverlayView.setVisibility(View.GONE);
        }
    }

    /**
     * Called when the progress dialog was cancelled.
     *
     * @param taskId for the task; {@code null} if there was no embedded task.
     */
    @Override
    public void onProgressDialogCancelled(@Nullable final Integer taskId) {
        cancelTasksAndUpdateProgress(false);
    }

    /**
     * Cancel all tasks, and if the progress is showing,
     * update it (it will check task manager status).
     *
     * @param forceShowProgress if {@code true} we'll force the progress dialog to show.
     */
    private void cancelTasksAndUpdateProgress(final boolean forceShowProgress) {
        if (mTaskManager != null) {
            mTaskManager.cancelAllTasks();
        }

        if ((mProgressOverlayView.getVisibility() == View.VISIBLE) || forceShowProgress) {
            updateProgressDialog();
        }
    }
}
