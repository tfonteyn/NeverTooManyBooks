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

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.UpgradeDatabase;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.scanner.GoogleBarcodeScanner;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * <strong>Note:</strong> yes, this is overkill for the startup. Call it an experiment.
 */
public class StartupViewModel
        extends ViewModel {

    /** Number of times the app has been started. */
    public static final String PREF_STARTUP_COUNT = "Startup.StartCount";
    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    public static final String PREF_STARTUP_COUNTDOWN = "Startup.StartCountdown";
    /** Number of app startup's between offers to backup. */

    private static final int PROMPT_WAIT_BACKUP;

    static {
        if (BuildConfig.DEBUG /* always */) {
            PROMPT_WAIT_BACKUP = 5;
        } else {
            PROMPT_WAIT_BACKUP = 5;
        }
    }

    /** TaskId holder. Added when started. Removed when stopped. */
    @NonNull
    private final Set<Integer> mAllTasks = new HashSet<>(6);

    private final MutableLiveData<Boolean> mTaskFinished = new MutableLiveData<>(false);
    private final MutableLiveData<Exception> mTaskException = new MutableLiveData<>();
    private final MutableLiveData<Integer> mTaskProgressMessage = new MutableLiveData<>();

    private final TaskListener<Boolean> mTaskListener = new TaskListener<Boolean>() {
        /**
         * Called when any startup task completes. If no more tasks, let the activity know.
         */
        @Override
        public void onTaskFinished(@NonNull final TaskFinishedMessage<Boolean> message) {
            synchronized (mAllTasks) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                    Logger.debug(this, "onTaskFinished", message);
                }
                mAllTasks.remove(message.taskId);
                if (mAllTasks.isEmpty()) {
                    mTaskFinished.setValue(true);
                }
            }
        }

        @Override
        public void onTaskProgress(@NonNull final TaskProgressMessage message) {
            if (message.values != null && message.values.length > 0) {
                mTaskProgressMessage.setValue((Integer) (message.values[0]));
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

    /**
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
     * Only provides the last the message. There is no back stack.
     *
     * @return message
     */
    public MutableLiveData<Integer> getTaskProgressMessage() {
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
        // Remove old logs
        Logger.clearLog();

        try {
            mDb = new DAO();
        } catch (@NonNull final DBHelper.UpgradeException e) {
            Logger.error(this, e, "startTasks");
            mTaskException.setValue(e);
            return;
        }


        if (mStartupTasksShouldBeStarted) {
            Locale locale = LocaleUtils.getLocale(context);

            int taskId = 0;
            // start these unconditionally
            startTask(new BuildLanguageMappingsTask(++taskId, locale, mTaskListener));
            startTask(new PreloadGoogleScanner(++taskId, mTaskListener));

            // this is not critical, once every so often is fine
            if (mDoPeriodicAction) {
                // cleaner must be started after the language mapper task.
                startTask(new DBCleanerTask(++taskId, mDb, locale, mTaskListener));
            }

            // on demand only
            if (PreferenceManager.getDefaultSharedPreferences(context)
                                 .getBoolean(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED,
                                             false)) {
                startTask(new RebuildFtsTask(++taskId, mDb, locale, mTaskListener));
            }

            // shouldn't be needed every single time.
            if (mDoPeriodicAction) {
                // analyse db should always be started as the last task.
                startTask(new AnalyzeDbTask(++taskId, mDb, ImageUtils.imagesAreCached(),
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

        @NonNull
        private final Locale mUserLocale;

        /**
         * Constructor.
         *
         * @param taskId       a task identifier, will be returned in the task listener.
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        BuildLanguageMappingsTask(final int taskId,
                                  @NonNull final Locale userLocale,
                                  @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
            mUserLocale = userLocale;
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("BuildLanguageMappingsTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(new TaskProgressMessage(mTaskId, R.string.progress_msg_upgrading));
            try {
                LanguageUtils.createLanguageMappingCache(mUserLocale);

            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                return false;
            }
            return true;
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

        protected PreloadGoogleScanner(final int taskId,
                                       @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
        }

        @Override
        protected Boolean doInBackground(final Void... voids) {
            Context context = App.getAppContext();

            GoogleBarcodeScanner.GoogleBarcodeScannerFactory factory =
                    new GoogleBarcodeScanner.GoogleBarcodeScannerFactory();
            if (factory.isAvailable(context)) {
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
        @NonNull
        private final Locale mUserLocale;

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
                      @NonNull final Locale userLocale,
                      @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
            mDb = db;
            mUserLocale = userLocale;
        }

        @WorkerThread
        @Override
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("DBCleanerTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(new TaskProgressMessage(mTaskId, R.string.progress_msg_optimizing));
            try {
                DBCleaner cleaner = new DBCleaner(mDb);

                // do a mass update of any languages not yet converted to ISO 639-2 codes
                cleaner.updateLanguages(mUserLocale);
                // clean/correct style UUID's on Bookshelves for deleted styles.
                cleaner.bookshelves();

                // check & log, but don't update yet... need more testing
                cleaner.maybeUpdate(true);
            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                return false;
            }
            return true;
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
        @NonNull
        private final Locale mUserLocale;

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
                       @NonNull final Locale userLocale,
                       @NonNull final TaskListener<Boolean> taskListener) {
            super(taskId, taskListener);
            mDb = db;
            mUserLocale = userLocale;
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("RebuildFtsTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(new TaskProgressMessage(mTaskId,
                                                    R.string.progress_msg_rebuilding_search_index));
            try {
                mDb.rebuildFts(mUserLocale);
            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(@NonNull final Boolean result) {
            super.onPostExecute(result);
            if (result) {
                PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                 .edit()
                                 .remove(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED)
                                 .apply();
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

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(new TaskProgressMessage(mTaskId, R.string.progress_msg_optimizing));
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

            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                return false;
            }
            return true;
        }
    }
}
