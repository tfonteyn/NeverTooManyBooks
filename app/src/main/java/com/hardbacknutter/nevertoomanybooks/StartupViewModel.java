/*
 * @Copyright 2018-2021 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.HashSet;

import com.hardbacknutter.nevertoomanybooks.backup.Backup;
import com.hardbacknutter.nevertoomanybooks.database.tasks.DBCleanerTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.OptimizeDbTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildFtsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildTitleOrderByColumnTask;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.BuildLanguageMappingsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * <strong>Note:</strong> yes, this is overkill for the startup. Call it an experiment.
 * We're using MutableLiveData to report back, but our tasks are LTasks.
 */
public class StartupViewModel
        extends ViewModel {

    /** Flag to indicate the OrderBy column for titles must be rebuild at startup. */
    @RebuildFlag
    public static final String PK_REBUILD_TITLE_OB = "startup.task.rebuild.title.sorting";

    /** Flag to indicate all indexes must be rebuild at startup. */
    @RebuildFlag
    public static final String PK_REBUILD_INDEXES = "startup.task.rebuild.index";
    /** Flag to indicate FTS rebuild is required at startup. */
    @RebuildFlag
    public static final String PK_REBUILD_FTS = "startup.task.rebuild.fts";
    /** Flag to indicate maintenance (cleaner) is required at startup. */
    @RebuildFlag
    public static final String PK_RUN_MAINTENANCE = "startup.task.maintenance";

    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    private static final String PK_MAINTENANCE_COUNTDOWN = "startup.startCountdown";
    /** Number of app startup's between some periodic action. */
    private static final int MAINTENANCE_COUNTDOWN = 5;

    /** Number of times the app has been started. */
    private static final String PK_STARTUP_COUNT = "startup.startCount";

    private final MutableLiveData<FinishedMessage<Void>> mFinished = new MutableLiveData<>();
    private final MutableLiveData<FinishedMessage<Exception>> mFailure = new MutableLiveData<>();
    private final MutableLiveData<ProgressMessage> mProgress = new MutableLiveData<>();

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
            // We don't care about the status; just finish
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

    /** Flag to ensure tasks are only ever started once. */
    private boolean mStartTasks = true;

    /** stage the startup is at. */
    private int mStartupStage;

    /** Flag we need to prompt the user to make a backup after startup. */
    private boolean mProposeBackup;

    /** Triggers periodic maintenance tasks. */
    private boolean mDoMaintenance;

    public static void schedule(@RebuildFlag @NonNull final String key,
                                final boolean flag) {
        final SharedPreferences.Editor ed = ServiceLocator.getGlobalPreferences().edit();
        if (flag) {
            ed.putBoolean(key, true);
        } else {
            ed.remove(key);
        }
        ed.apply();
    }

    public boolean isRunning() {
        return !mAllTasks.isEmpty();
    }

    boolean isProposeBackup() {
        return mProposeBackup;
    }

    int getStartupStage() {
        return mStartupStage;
    }

    int getNextStartupStage() {
        if (mStartupStage < 5) {
            mStartupStage++;
        }
        return mStartupStage;
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     */
    public void init(@NonNull final Context context) {
        if (mStartTasks) {
            // from here on, we have access to our log file
            Logger.cycleLogs();

            // prepare the maintenance flags and counters.
            final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
            final int maintenanceCountdown = global
                    .getInt(PK_MAINTENANCE_COUNTDOWN, MAINTENANCE_COUNTDOWN);
            final int backupCountdown = global
                    .getInt(Backup.PK_BACKUP_COUNTDOWN, Backup.BACKUP_COUNTDOWN_DEFAULT);

            global.edit()
                  .putInt(PK_MAINTENANCE_COUNTDOWN,
                          maintenanceCountdown == 0 ? MAINTENANCE_COUNTDOWN
                                                    : maintenanceCountdown - 1)
                  .putInt(Backup.PK_BACKUP_COUNTDOWN,
                          backupCountdown == 0 ? Backup.BACKUP_COUNTDOWN_DEFAULT
                                               : backupCountdown - 1)

                  // The number of times the app was opened.
                  .putInt(PK_STARTUP_COUNT, global.getInt(PK_STARTUP_COUNT, 0) + 1)
                  .apply();

            mDoMaintenance = maintenanceCountdown == 0;
            mProposeBackup = backupCountdown == 0;
        }
    }


    /**
     * Run a number of essential tasks (sequentially).
     *
     * @param context Current context
     */
    boolean startTasks(@NonNull final Context context) {
        if (!mStartTasks) {
            return false;
        }

        // Clear the flag
        mStartTasks = false;

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        // unconditional
        startTask(new BuildLanguageMappingsTask(mTaskListener));

        boolean optimizeDb = false;

        if (mDoMaintenance || global.getBoolean(PK_RUN_MAINTENANCE, false)) {
            // cleaner must be started after the language mapper task,
            // but before the rebuild tasks.
            startTask(new DBCleanerTask(mTaskListener));
            optimizeDb = true;
        }

        if (global.getBoolean(PK_REBUILD_TITLE_OB, false)) {
            startTask(new RebuildTitleOrderByColumnTask(mTaskListener));
            optimizeDb = true;
        }

        if (global.getBoolean(PK_REBUILD_INDEXES, false)) {
            startTask(new RebuildIndexesTask(mTaskListener));
            optimizeDb = true;
        }

        if (global.getBoolean(PK_REBUILD_FTS, false)) {
            startTask(new RebuildFtsTask(mTaskListener));
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

        return true;
    }

    private void startTask(@NonNull final StartupTask task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task.getTaskId());
            task.start();
        }
    }

    /**
     * Called when the task successfully finishes.
     *
     * @return the Result which can be considered to be complete and correct.
     */
    @NonNull
    public LiveData<FinishedMessage<Void>> onFinished() {
        return mFinished;
    }

    /**
     * Called when the task fails with an Exception.
     *
     * @return the result is the Exception
     */
    @NonNull
    public LiveData<FinishedMessage<Exception>> onFailure() {
        return mFailure;
    }

    /**
     * Forwards progress messages for the client to display.
     *
     * @return a {@link ProgressMessage} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<ProgressMessage> onProgress() {
        return mProgress;
    }

    public interface StartupTask {

        @AnyThread
        int getTaskId();

        @UiThread
        void start();
    }

    @StringDef({PK_REBUILD_FTS,
                PK_REBUILD_INDEXES,
                PK_REBUILD_TITLE_OB,
                PK_RUN_MAINTENANCE})
    @Retention(RetentionPolicy.SOURCE)
    @interface RebuildFlag {

    }
}
