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
package com.hardbacknutter.nevertoomanybooks.baseactivity;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.ManagedTask;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.MessageSwitch;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManager;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManagerController;
import com.hardbacknutter.nevertoomanybooks.tasks.managedtasks.TaskManagerListener;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

/**
 * TODO: Remove this! Fragment/ViewModel makes BaseActivityWithTasks mostly redundant.
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
 */
public abstract class BaseActivityWithTasks
        extends BaseActivity {

    private static final String TAG = "BaseActivityWithTasks";

    private static final String BKEY_TASK_MANAGER_ID = TAG + ":TaskManagerId";

    /** id of associated TaskManager. */
    private long mTaskManagerId;
    /** Associated TaskManager. */
    @Nullable
    private TaskManager mTaskManager;

//    private ProgressDialogFragment mProgressDialog;

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

    private final TaskManagerListener mTaskManagerListener = new TaskManagerListener() {

        @Override
        public void onTaskFinished(@NonNull final ManagedTask task) {
            String msg = task.getFinalMessage();
            if (msg != null && !msg.isEmpty()) {
                UserMessage.show(BaseActivityWithTasks.this, msg);
            }
        }

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
                runOnUiThread(() -> updateProgressDialog());
            }
        }

        @Override
        public void onTaskUserMessage(@NonNull final String message) {
            UserMessage.show(BaseActivityWithTasks.this, message);
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main_nav_legacy_tasks;
    }

    @Override
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

    /**
     * When the user clicks 'back/up', clean up any running tasks.
     */
    @Override
    public void onBackPressed() {
        if (mTaskManager != null) {
            mTaskManager.cancelAllTasks();
        }
        updateProgressDialog();
        super.onBackPressed();
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        // If we are finishing, we don't care about active tasks.
        if (!isFinishing()) {
            // Restore/create mTaskManager.
            getTaskManager();
            TaskManager.MESSAGE_SWITCH.addListener(mTaskManagerId, true, mTaskManagerListener);
        }
    }

    @Override
    @CallSuper
    protected void onPause() {
        // Stop listening
        if (mTaskManagerId != 0) {
            TaskManager.MESSAGE_SWITCH.removeListener(mTaskManagerId, mTaskManagerListener);
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
     * Save the TaskManager id for later retrieval.
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
                    Logger.warnWithStackTrace(this, this, "Have ID(" + mTaskManagerId + "),"
                                                          + " but controller not found");
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
     * Dismiss the Progress Dialog.
     */
    private void closeProgressDialog() {
        if (mProgressOverlayView != null) {
            mProgressOverlayView.setVisibility(View.GONE);
        }
    }
}
