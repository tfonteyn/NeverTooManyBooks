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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.Collection;
import java.util.HashSet;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.tasks.AnalyzeDbTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.DBCleanerTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildFtsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildOrderByTitleColumnsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.Scheduler;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.GoogleBarcodeScanner;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;
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

    private final MutableLiveData<Integer> mFatalMsgId = new MutableLiveData<>();

    private final MutableLiveData<Boolean> mAllTasksFinished = new MutableLiveData<>(false);
    private final MutableLiveData<Exception> mTaskException = new MutableLiveData<>();
    /** Using MutableLiveData as we actually want re-delivery after a device rotation. */
    private final MutableLiveData<String> mTaskProgressMessage = new MutableLiveData<>();

    private final TaskListener<Boolean> mTaskListener = new TaskListener<Boolean>() {
        /**
         * Called when any startup task completes. If no more tasks, let the activity know.
         */
        @Override
        public void onFinished(@NonNull final FinishMessage<Boolean> message) {
            synchronized (mAllTasks) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                    Log.d(TAG, "onFinished|" + message);
                }
                mAllTasks.remove(message.taskId);
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
    private boolean mStartTasks = true;

    /** Flag we need to prompt the user to make a backup after startup. */
    private boolean mProposeBackup;
    /** Flag indicating a backup is required after startup. */
    private boolean mBackupRequired;

    /** Triggers periodic maintenance tasks. */
    private boolean mDoMaintenance;

    /** Indicates the upgrade message has been shown. */
    private boolean mUpgradeMessageShown;

    /**
     * Updated after a (any) task finishes with an exception.
     *
     * @return exception, or {@code null} for none.
     */
    @NonNull
    public MutableLiveData<Integer> onFatalMsgId() {
        return mFatalMsgId;
    }

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

        Notifier.init(context);

        final int msgId = AppDir.init(context);
        if (msgId != 0) {
            mFatalMsgId.setValue(msgId);
            return;
        }

        Logger.cycleLogs(context);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final int maintenanceCountdown = prefs
                .getInt(PREF_MAINTENANCE_COUNTDOWN, MAINTENANCE_COUNTDOWN);
        final int backupCountdown = prefs
                .getInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN, Prefs.STARTUP_BACKUP_COUNTDOWN);

        prefs.edit()
             .putInt(PREF_MAINTENANCE_COUNTDOWN,
                     maintenanceCountdown == 0 ? MAINTENANCE_COUNTDOWN : maintenanceCountdown - 1)
             .putInt(Prefs.PREF_STARTUP_BACKUP_COUNTDOWN,
                     backupCountdown == 0 ? Prefs.STARTUP_BACKUP_COUNTDOWN : backupCountdown - 1)

             // The number of times the app was opened.
             .putInt(PREF_STARTUP_COUNT, prefs.getInt(PREF_STARTUP_COUNT, 0) + 1)
             .apply();

        mDoMaintenance = maintenanceCountdown == 0;
        mProposeBackup = backupCountdown == 0;
    }

    public boolean isStartTasks() {
        return mStartTasks;
    }

    public boolean isProposeBackup() {
        return mProposeBackup;
    }

    public boolean isBackupRequired() {
        return mBackupRequired;
    }

    public void setBackupRequired() {
        mBackupRequired = true;
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
        mStartTasks = false;

        try {
            mDb = new DAO(TAG);

        } catch (@NonNull final Exception e) {
            Logger.error(context, TAG, e, "startTasks");
            mTaskException.setValue(e);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        int taskId = 0;
        // start these unconditionally
        startTask(new LanguageUtils.BuildLanguageMappingsTask(++taskId, mTaskListener), false);
        // perhaps delay this until the user selects the google scanner?
        startTask(new GoogleBarcodeScanner.PreloadGoogleScanner(++taskId, mTaskListener), true);

        // this is not critical, once every so often is fine
        if (mDoMaintenance) {
            // cleaner must be started after the language mapper task.
            startTask(new DBCleanerTask(++taskId, mDb, mTaskListener), false);
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_ORDERBY_COLUMNS, false)) {
            startTask(new RebuildOrderByTitleColumnsTask(++taskId, mDb, mTaskListener), false);
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_INDEXES, false)) {
            startTask(new RebuildIndexesTask(++taskId, mDb, mTaskListener), false);
        }

        // on demand only
        if (prefs.getBoolean(Scheduler.PREF_REBUILD_FTS, false)) {
            startTask(new RebuildFtsTask(++taskId, mDb, mTaskListener), false);
        }

        // shouldn't be needed every single time.
        if (mDoMaintenance) {
            // analyse db should always be started as the last task.
            startTask(new AnalyzeDbTask(++taskId, mDb, ImageUtils.imagesAreCached(context),
                                        mTaskListener), false);
        }

        synchronized (mAllTasks) {
            if (mAllTasks.isEmpty()) {
                mAllTasksFinished.setValue(true);
            }
        }
    }

    private void startTask(@NonNull final TaskBase<Void, Boolean> task,
                           final boolean inParallel) {
        synchronized (mAllTasks) {
            mAllTasks.add(task.getTaskId());
            if (inParallel) {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                task.execute();
            }
        }
    }
}
