/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Collection;
import java.util.HashSet;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.tasks.DBCleanerTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.OptimizeDbTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildFtsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildOrderByTitleColumnsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.Scheduler;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.BuildLanguageMappingsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * <strong>Note:</strong> yes, this is overkill for the startup. Call it an experiment.
 * It's also an unhealthy mix of VMTask and LTask components.
 * We're using VMTask MutableLiveData to report back, but our tasks are LTasks.
 */
public class StartupViewModel
        extends VMTask<Void> {

    public static final String PREF_PREFIX = "startup.";
    /** Log tag. */
    private static final String TAG = "StartupViewModel";
    /** Number of times the app has been started. */
    private static final String PREF_STARTUP_COUNT = PREF_PREFIX + "startCount";
    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    private static final String PREF_MAINTENANCE_COUNTDOWN = PREF_PREFIX + "startCountdown";

    /** Number of app startup's between some periodic action. */
    private static final int MAINTENANCE_COUNTDOWN = 5;
    /** TaskId holder. Added when started. Removed when stopped. */
    @NonNull
    private final Collection<Integer> mAllTasks = new HashSet<>(6);

    private final TaskListener<Boolean> mTaskListener = new TaskListener<Boolean>() {
        /**
         * Called when any startup task completes. If no more tasks, let the activity know.
         */
        @Override
        public void onFinished(@NonNull final FinishedMessage<Boolean> message) {
            cleanup(message.taskId);
        }

        @Override
        public void onCancelled(@NonNull final FinishedMessage<Boolean> message) {
            cleanup(message.taskId);
        }

        @Override
        public void onFailure(@NonNull final FinishedMessage<Exception> message) {
            // We don't care about the status.
            cleanup(message.taskId);
        }

        private void cleanup(final int taskId) {
            synchronized (mAllTasks) {
                mAllTasks.remove(taskId);
                if (!isRunning()) {
                    mFinished.setValue(null);
                }
            }
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            mProgress.setValue(message);
        }
    };

    /** Database Access. */
    private DAO mDb;
    /** Flag to ensure tasks are only ever started once. */
    private boolean mIsFirstStart = true;
    /** stage the startup is at. */
    private int mStartupStage;

    /** Flag we need to prompt the user to make a backup after startup. */
    private boolean mProposeBackup;

    /** Triggers periodic maintenance tasks. */
    private boolean mDoMaintenance;

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mDb != null) {
            mDb.close();
        }
    }

    @Override
    public boolean isRunning() {
        return !mAllTasks.isEmpty() || super.isRunning();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     */
    public void init(@NonNull final Context context) {
        if (mIsFirstStart) {
            // from here on, we have access to our log file
            Logger.cycleLogs(context);

            // prepare the maintenance flags and counters.
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final int maintenanceCountdown = prefs
                    .getInt(PREF_MAINTENANCE_COUNTDOWN, MAINTENANCE_COUNTDOWN);
            final int backupCountdown = prefs
                    .getInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN, Prefs.STARTUP_BACKUP_COUNTDOWN);

            prefs.edit()
                 .putInt(PREF_MAINTENANCE_COUNTDOWN,
                         maintenanceCountdown == 0 ? MAINTENANCE_COUNTDOWN
                                                   : maintenanceCountdown - 1)
                 .putInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN,
                         backupCountdown == 0 ? Prefs.STARTUP_BACKUP_COUNTDOWN
                                              : backupCountdown - 1)

                 // The number of times the app was opened.
                 .putInt(PREF_STARTUP_COUNT, prefs.getInt(PREF_STARTUP_COUNT, 0) + 1)
                 .apply();

            mDoMaintenance = maintenanceCountdown == 0;
            mProposeBackup = backupCountdown == 0;
        }
    }

    public boolean isStartTasks() {
        return mIsFirstStart;
    }

    public boolean isProposeBackup() {
        return mProposeBackup;
    }

    public int getStartupStage() {
        return mStartupStage;
    }

    public void incStartupStage() {
        mStartupStage++;
    }

    /**
     * We use the standard AsyncTask execute, so tasks are run serially.
     *
     * @param context Current context
     */
    public void startTasks(@NonNull final Context context) {

        // Clear the flag
        mIsFirstStart = false;

        try {
            // this can trigger a database upgrade (or the initial creation)
            // which is why we catch ALL exceptions here.
            mDb = new DAO(TAG);

        } catch (@NonNull final Exception e) {
            Logger.error(context, TAG, e, "startTasks");
            mFailure.setValue(new FinishedMessage<>(R.id.TASK_ID_STARTUP_COORDINATOR, e));
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean optimizeDb = false;

        // unconditional
        startTask(new BuildLanguageMappingsTask(mTaskListener));

        // this is not critical, once every so often is fine
        if (mDoMaintenance) {
            // cleaner must be started after the language mapper task,
            // but before the rebuild tasks.
            startTask(new DBCleanerTask(mDb, mTaskListener));
            optimizeDb = true;
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_ORDERBY_COLUMNS, false)) {
            startTask(new RebuildOrderByTitleColumnsTask(mDb, mTaskListener));
            optimizeDb = true;
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_INDEXES, false)) {
            startTask(new RebuildIndexesTask(mTaskListener));
            optimizeDb = true;
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_FTS, false)) {
            startTask(new RebuildFtsTask(mDb, mTaskListener));
            optimizeDb = true;
        }

        // triggered by any of the above as needed
        // This should always be the last task.
        if (optimizeDb) {
            startTask(new OptimizeDbTask(mTaskListener));
        }

        if (!isRunning()) {
            mFinished.setValue(null);
        }
    }

    private void startTask(@NonNull final LTask<Boolean> task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task.getTaskId());
            task.execute();
        }
    }
}
