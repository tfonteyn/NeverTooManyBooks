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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.GoogleBarcodeScanner;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;

/**
 * <strong>Note:</strong> yes, this is overkill for the startup. Call it an experiment.
 */
public class StartupViewModel
        extends ViewModel {

    private static final String TAG = "StartupViewModel";

    private static final String PREF_PREFIX = "startup.";

    /** the 'LastVersion' i.e. the version which was installed before the current one. */
    public static final String PREF_STARTUP_LAST_VERSION = PREF_PREFIX + "lastVersion";
    /** Number of times the app has been started. */
    private static final String PREF_STARTUP_COUNT = PREF_PREFIX + "startCount";
    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    private static final String PREF_STARTUP_COUNTDOWN = PREF_PREFIX + "startCountdown";
    /** Flag to indicate FTS rebuild is required at startup. */
    private static final String PREF_STARTUP_FTS_REBUILD_REQUIRED = PREF_PREFIX + "rebuild.fts";
    /** Flag to indicate OrderBy columns must be rebuild at startup. */
    private static final String PREF_STARTUP_ORDERBY_TITLE_REBUILD_REQUIRED =
            PREF_PREFIX + "rebuild.ob.title";

    /** Number of app startup's between offers to backup. */
    private static final int PROMPT_WAIT_BACKUP = 5;

    /** TaskId holder. Added when started. Removed when stopped. */
    @NonNull
    private final Set<Integer> mAllTasks = new HashSet<>(6);

    private final MutableLiveData<Boolean> mTaskFinished = new MutableLiveData<>(false);
    private final MutableLiveData<Exception> mTaskException = new MutableLiveData<>();
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
                    mTaskFinished.setValue(true);
                }
            }
        }

        @Override
        public void onProgress(@NonNull final ProgressMessage message) {
            if (message.text != null) {
                mTaskProgressMessage.setValue(message.text);
            }
        }
    };

    /** Database Access. */
    private DAO mDb;
    /** Flag to ensure tasks are only ever started once. */
    private boolean mStartupTasksShouldBeStarted = true;
    /** Flag indicating a backup is required after startup. */
    private boolean mBackupRequired;

    private boolean mDoPeriodicAction;

    /** Set the flag to indicate an FTS rebuild is required. */
    @SuppressWarnings("unused")
    public static void setScheduleFtsRebuild(@NonNull final Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                         .edit().putBoolean(PREF_STARTUP_FTS_REBUILD_REQUIRED, true).apply();
    }

    /** Set or remove the flag to indicate an OrderBy column rebuild is required. */
    public static void setScheduleOrderByRebuild(@NonNull final Context context,
                                                 final boolean flag) {
        SharedPreferences.Editor ed = PreferenceManager
                .getDefaultSharedPreferences(context).edit();
        if (flag) {
            ed.putBoolean(PREF_STARTUP_ORDERBY_TITLE_REBUILD_REQUIRED, true);
        } else {
            ed.remove(PREF_STARTUP_ORDERBY_TITLE_REBUILD_REQUIRED);
        }
        ed.apply();
    }

    /**
     * Developer warning: this is not a UI update.
     *
     * @return {@code true} if all tasks are finished.
     */
    public MutableLiveData<Boolean> getTaskFinished() {
        return mTaskFinished;
    }

    /**
     * Updated after a (any) task finishes with an exception.
     *
     * @return exception, or {@code null} for none.
     */
    public MutableLiveData<Exception> getTaskException() {
        return mTaskException;
    }

    /**
     * Only provides the last message. There is no queue.
     *
     * @return message
     */
    public MutableLiveData<String> getTaskProgressMessage() {
        return mTaskProgressMessage;
    }

    @Override
    protected void onCleared() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    public boolean isStartupTasksShouldBeStarted() {
        return mStartupTasksShouldBeStarted;
    }

    public boolean isDoPeriodicAction() {
        return mDoPeriodicAction;
    }

    public boolean isBackupRequired() {
        return mBackupRequired;
    }

    public void setBackupRequired(final boolean backupRequired) {
        mBackupRequired = backupRequired;
    }

    /**
     * We use the standard AsyncTask execute, so tasks are run serially.
     */
    public void startTasks(@NonNull final Context context) {

        Logger.cycleLogs(context);

        try {
            mDb = new DAO();

        } catch (@NonNull final DBHelper.UpgradeException e) {
            Logger.error(App.getAppContext(), TAG, e, "startTasks");
            mTaskException.setValue(e);
            return;
        }

        if (mStartupTasksShouldBeStarted) {
            int taskId = 0;
            // start these unconditionally
            startTask(new BuildLanguageMappingsTask(++taskId, mTaskListener));
            startTask(new PreloadGoogleScanner(++taskId, mTaskListener));

            // this is not critical, once every so often is fine
            if (mDoPeriodicAction) {
                // cleaner must be started after the language mapper task.
                startTask(new DBCleanerTask(++taskId, mDb, mTaskListener));
            }

            // on demand only
            if (PreferenceManager.getDefaultSharedPreferences(context)
                                 .getBoolean(PREF_STARTUP_ORDERBY_TITLE_REBUILD_REQUIRED,
                                             false)) {
                startTask(new RebuildOrderByTitleColumnsTask(++taskId, mDb, mTaskListener));
            }

            // on demand only
            if (PreferenceManager.getDefaultSharedPreferences(context)
                                 .getBoolean(PREF_STARTUP_FTS_REBUILD_REQUIRED,
                                             false)) {
                startTask(new RebuildFtsTask(++taskId, mDb, mTaskListener));
            }

            // shouldn't be needed every single time.
            if (mDoPeriodicAction) {
                // analyse db should always be started as the last task.
                startTask(new AnalyzeDbTask(++taskId, mDb, ImageUtils.imagesAreCached(context),
                                            mTaskListener));
            }

            // Clear the flag
            mStartupTasksShouldBeStarted = false;

            // If no tasks were queued, then move on to next stage. Otherwise, the completed
            // tasks will cause the next stage to start.
            synchronized (mAllTasks) {
                if (mAllTasks.isEmpty()) {
                    mTaskFinished.setValue(true);
                }
            }
        }
    }

    private void startTask(@NonNull final TaskBase<Boolean> task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task.getId());
            task.execute();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     */
    public void init(@NonNull final Context context) {
        mDoPeriodicAction = decreaseStartupCounters(context);
    }

    /**
     * Decrease and store the number of times the app was opened.
     * Used periodic actions.
     *
     * @return {@code true} when counter reached 0
     */
    private boolean decreaseStartupCounters(@NonNull final Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int opened = prefs.getInt(PREF_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        int startCount = prefs.getInt(PREF_STARTUP_COUNT, 0) + 1;

        final SharedPreferences.Editor ed = prefs.edit();
        if (opened == 0) {
            ed.putInt(PREF_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        } else {
            ed.putInt(PREF_STARTUP_COUNTDOWN, opened - 1);
        }
        ed.putInt(PREF_STARTUP_COUNT, startCount)
          .apply();

        return opened == 0;
    }

    /**
     * Build the dedicated SharedPreferences file with the language mappings.
     * Only build once per Locale.
     */
    static class BuildLanguageMappingsTask
            extends TaskBase<Boolean> {

        /**
         * Constructor.
         *
         * @param taskId       a task identifier, will be returned in the task listener.
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        BuildLanguageMappingsTask(final int taskId,
                                  @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("BuildLanguageMappingsTask");
            Context context = App.getLocalizedAppContext();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Log.d(TAG, "doInBackground|taskId=" + getId());
            }
            publishProgress(new TaskListener.ProgressMessage(mTaskId, context.getString(
                    R.string.progress_msg_optimizing)));
            try {
                LanguageUtils.createLanguageMappingCache();
                return true;

            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);
                mException = e;
                return false;
            }
        }
    }

    /**
     * If the Google barcode scanner can be loaded, create a dummy instance to
     * force it to download the native library if not done already.
     * <p>
     * This can be seen in the device logs as:
     * <p>
     * I/Vision: Loading library libbarhopper.so
     * I/Vision: Library not found: /data/user/0/com.google.android.gms/app_vision/barcode/
     * libs/x86/libbarhopper.so
     * I/Vision: libbarhopper.so library load status: false
     * Request download for engine barcode
     */
    static class PreloadGoogleScanner
            extends TaskBase<Boolean> {

        PreloadGoogleScanner(final int taskId,
                             @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Context context = App.getAppContext();

            GoogleBarcodeScanner.GoogleBarcodeScannerFactory factory =
                    new GoogleBarcodeScanner.GoogleBarcodeScannerFactory();
            if (factory.isAvailable(context)) {
                // trigger the download if needed.
                factory.newInstance(context);
            }
            return true;
        }
    }

    /**
     * Data cleaning. Done on each startup.
     */
    static class DBCleanerTask
            extends TaskBase<Boolean> {

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /**
         * Constructor.
         *
         * @param taskId       a task identifier, will be returned in the task finished listener.
         * @param db           Database Access
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        DBCleanerTask(final int taskId,
                      @NonNull final DAO db,
                      @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
            mDb = db;
        }

        @WorkerThread
        @Override
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("DBCleanerTask");
            Context context = App.getLocalizedAppContext();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Log.d(TAG, "doInBackground|taskId=" + getId());
            }
            publishProgress(new TaskListener.ProgressMessage(mTaskId, context.getString(
                    R.string.progress_msg_optimizing)));
            try {
                DBCleaner cleaner = new DBCleaner(mDb);

                // do a mass update of any languages not yet converted to ISO 639-2 codes
                cleaner.updateLanguages(context);
                // clean/correct style UUID's on Bookshelves for deleted styles.
                cleaner.bookshelves();

                // check & log, but don't update yet... need more testing
                cleaner.maybeUpdate(true);
                return true;

            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);
                mException = e;
                return false;
            }

        }
    }

    /**
     * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
     */
    static class RebuildFtsTask
            extends TaskBase<Boolean> {

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /**
         * Constructor.
         *
         * @param taskId       a task identifier, will be returned in the task finished listener.
         * @param db           Database Access
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        RebuildFtsTask(final int taskId,
                       @NonNull final DAO db,
                       @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
            mDb = db;
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("RebuildFtsTask");
            Context context = App.getLocalizedAppContext();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Log.d(TAG, "doInBackground|taskId=" + getId());
            }
            publishProgress(new TaskListener.ProgressMessage(mTaskId, context.getString(
                    R.string.progress_msg_rebuilding_search_index)));
            try {
                mDb.rebuildFts();
                PreferenceManager.getDefaultSharedPreferences(context)
                                 .edit()
                                 .remove(PREF_STARTUP_FTS_REBUILD_REQUIRED)
                                 .apply();
                return true;

            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);
                mException = e;
                return false;
            }

        }
    }

    /**
     * Task to rebuild all OrderBy columns in background.
     */
    static class RebuildOrderByTitleColumnsTask
            extends TaskBase<Boolean> {

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /**
         * Constructor.
         *
         * @param taskId       a task identifier, will be returned in the task finished listener.
         * @param db           Database Access
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        RebuildOrderByTitleColumnsTask(final int taskId,
                                       @NonNull final DAO db,
                                       @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
            mDb = db;
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("RebuildOrderByTitleColumnsTask");
            Context context = App.getLocalizedAppContext();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Log.d(TAG, "doInBackground|taskId=" + getId());
            }
            // incorrect progress message, but it's half-true.
            publishProgress(new TaskListener.ProgressMessage(mTaskId, context.getString(
                    R.string.progress_msg_rebuilding_search_index)));
            try {
                boolean reorder = Prefs.reorderTitleForSorting(context);
                mDb.rebuildOrderByTitleColumns(reorder);
                return true;

            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);
                mException = e;
                return false;
            } finally {
                // regardless of result, always disable as we do not want to rebuild/fail/rebuild...
                setScheduleOrderByRebuild(context, false);
            }
        }
    }

    /**
     * Run 'analyse' on our databases.
     */
    static class AnalyzeDbTask
            extends TaskBase<Boolean> {

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        private final boolean mDoCoversDb;

        /**
         * @param taskId       a task identifier, will be returned in the task finished listener.
         * @param db           Database Access
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        AnalyzeDbTask(final int taskId,
                      @NonNull final DAO db,
                      final boolean doCoversDb,
                      @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
            mDb = db;
            mDoCoversDb = doCoversDb;
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("AnalyzeDbTask");
            Context context = App.getLocalizedAppContext();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Log.d(TAG, "doInBackground|taskId=" + getId());
            }
            publishProgress(new TaskListener.ProgressMessage(mTaskId, context.getString(
                    R.string.progress_msg_optimizing)));
            try {
                // small hack to make sure we always update the triggers.
                // Makes creating/modifying triggers MUCH easier.
                if (BuildConfig.DEBUG /* always */) {
                    mDb.recreateTriggers();
                }

                mDb.analyze();
                if (mDoCoversDb) {
                    CoversDAO.analyze();
                }
                return true;

            } catch (@NonNull final RuntimeException e) {
                Logger.error(context, TAG, e);
                mException = e;
                return false;
            }

        }
    }
}
