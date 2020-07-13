/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.Collection;
import java.util.HashSet;

import com.hardbacknutter.nevertoomanybooks.covers.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.tasks.DBCleanerTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.OptimizeDbTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildFtsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildOrderByTitleColumnsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.Scheduler;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.GoogleBarcodeScanner;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.BuildLanguageMappingsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.UpgradeMessageManager;

/**
 * <strong>Note:</strong> yes, this is overkill for the startup. Call it an experiment.
 */
public class StartupViewModel
        extends ViewModel {

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

    private final MutableLiveData<Boolean> mAllTasksFinished = new MutableLiveData<>(false);
    private final MutableLiveData<Exception> mTaskException = new MutableLiveData<>();
    /** Using MutableLiveData as we actually want re-delivery after a device rotation. */
    private final MutableLiveData<String> mTaskProgressMessage = new MutableLiveData<>();

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
                if (mAllTasks.isEmpty()) {
                    mAllTasksFinished.setValue(true);
                }
            }
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            mTaskProgressMessage.setValue(message.text);
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

    /** Indicates the upgrade message has been shown. */
    private boolean mUpgradeMessageShown;

    /**
     * Developer warning: this is not a UI update.
     *
     * @return {@code true} if all tasks are finished.
     */
    @NonNull
    public MutableLiveData<Boolean> onAllTasksFinished() {
        return mAllTasksFinished;
    }

    /**
     * Updated after a (any) task finishes with an exception.
     *
     * @return exception, or {@code null} for none.
     */
    @NonNull
    public MutableLiveData<Exception> onTaskException() {
        return mTaskException;
    }

    /**
     * Only provides the last message. There is no queue.
     *
     * @return message
     */
    @NonNull
    public MutableLiveData<String> onTaskProgress() {
        return mTaskProgressMessage;
    }

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
        }
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
                         maintenanceCountdown == 0 ? MAINTENANCE_COUNTDOWN :
                         maintenanceCountdown - 1)
                 .putInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN,
                         backupCountdown == 0 ? Prefs.STARTUP_BACKUP_COUNTDOWN :
                         backupCountdown - 1)

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
     * Get a potential upgrade-message.
     *
     * @param context Current context
     *
     * @return message or {@code null} if none
     */
    @Nullable
    public String getUpgradeMessage(@NonNull final Context context) {
        // only show the message once for each run (until user confirmed reading it)
        if (!mUpgradeMessageShown) {
            mUpgradeMessageShown = true;
            final String msg = UpgradeMessageManager.getUpgradeMessage(context);
            if (!msg.isEmpty()) {
                return msg;
            }
        }
        return null;
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
            mDb = new DAO(TAG);

        } catch (@NonNull final Exception e) {
            Logger.error(context, TAG, e, "startTasks");
            mTaskException.setValue(e);
            return;
        }

        // Start these as fire-and-forget runnable; no need to wait for them.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new BuildLanguageMappingsTask());

        // perhaps delay this until the user selects the google scanner?
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new GoogleBarcodeScanner.PreloadGoogleScanner());


        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int taskId = 0;
        boolean optimizeDb = false;

        // this is not critical, once every so often is fine
        if (mDoMaintenance) {
            // cleaner must be started after the language mapper task,
            // but before the rebuild tasks.
            startTask(new DBCleanerTask(++taskId, mDb, mTaskListener));
            optimizeDb = true;
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_ORDERBY_COLUMNS, false)) {
            startTask(new RebuildOrderByTitleColumnsTask(++taskId, mDb, mTaskListener));
            optimizeDb = true;
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_INDEXES, false)) {
            startTask(new RebuildIndexesTask(++taskId, mTaskListener));
            optimizeDb = true;
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_FTS, false)) {
            startTask(new RebuildFtsTask(++taskId, mDb, mTaskListener));
            optimizeDb = true;
        }

        // triggered by any of the above as needed
        if (optimizeDb) {
            // optimize db should always be started as the last task.
            startTask(new OptimizeDbTask(++taskId, ImageUtils.imagesAreCached(context),
                                         mTaskListener));
        }

        synchronized (mAllTasks) {
            if (mAllTasks.isEmpty()) {
                mAllTasksFinished.setValue(true);
            }
        }
    }

    private void startTask(@NonNull final LTask<Boolean> task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task.getTaskId());
            task.execute();
        }
    }
}
