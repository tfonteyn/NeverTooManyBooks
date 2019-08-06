/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.viewmodels;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertomanybooks.database.DAO;
import com.hardbacknutter.nevertomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertomanybooks.database.DBHelper;
import com.hardbacknutter.nevertomanybooks.database.UpgradeDatabase;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;

/**
 * <b>Note:</b> yes, this is overkill for the startup. Call it an experiment.
 */
public class StartupViewModel
        extends ViewModel {

    /** TaskId holder. Added when started. Removed when stopped. */
    @NonNull
    private final Set<Integer> mAllTasks = new HashSet<>(6);

    private final MutableLiveData<Boolean> mTaskFinished = new MutableLiveData<>(false);
    private final MutableLiveData<Exception> mTaskException = new MutableLiveData<>();
    private final MutableLiveData<Integer> mTaskProgressMessage = new MutableLiveData<>();

    private final TaskListener<Void> mTaskListener = new TaskListener<Void>() {
        /**
         * Called when any startup task completes. If no more tasks, let the activity know.
         */
        @Override
        public void onTaskFinished(@NonNull final TaskFinishedMessage<Void> message) {
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
    private boolean startupTasksShouldBeStarted = true;

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
        return startupTasksShouldBeStarted;
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
            Logger.warn(this, "startTasks", e.getLocalizedMessage());
            mTaskException.setValue(e);
            return;
        }


        if (startupTasksShouldBeStarted) {
            int taskId = 0;
            startTask(new BuildLanguageMappingsTask(++taskId, mTaskListener));

            // cleaner must be started after the language mapper task.
            startTask(new DBCleanerTask(++taskId, mDb, mTaskListener));

            if (PreferenceManager.getDefaultSharedPreferences(context)
                                 .getBoolean(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED,
                                             false)) {
                startTask(new RebuildFtsTask(++taskId, mDb, mTaskListener));
            }

            // analyse db should always be started as the last task.
            startTask(new AnalyzeDbTask(++taskId, mDb, mTaskListener));

            // Clear the flag
            startupTasksShouldBeStarted = false;

            // If no tasks were queued, then move on to next stage. Otherwise, the completed
            // tasks will cause the next stage to start.
            synchronized (mAllTasks) {
                if (mAllTasks.isEmpty()) {
                    mTaskFinished.setValue(true);
                }
            }
        }
    }

    private void startTask(@NonNull final TaskBase<Void> task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task.getId());
            task.execute();
        }
    }

    /**
     * Build the dedicated SharedPreferences file with the language mappings.
     * Only build once per Locale.
     */
    static class BuildLanguageMappingsTask
            extends TaskBase<Void> {

        /**
         * Constructor.
         *
         * @param taskId       a task identifier, will be returned in the task listener.
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        BuildLanguageMappingsTask(final int taskId,
                                  @NonNull final TaskListener<Void> taskListener) {
            super(taskId, taskListener);
        }

        @Override
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("BuildLanguageMappingsTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(new TaskProgressMessage(mTaskId, R.string.progress_msg_upgrading));
            try {
                LocaleUtils.createLanguageMappingCache();

            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
            }
            return null;
        }
    }

    /**
     * Data cleaning. Done on each startup.
     */
    static class DBCleanerTask
            extends TaskBase<Void> {

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
                      @NonNull final TaskListener<Void> taskListener) {
            super(taskId, taskListener);
            mDb = db;
        }

        @WorkerThread
        @Override
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("DBCleanerTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(new TaskProgressMessage(mTaskId, R.string.progress_msg_optimizing));
            try {
                DBCleaner cleaner = new DBCleaner(mDb);

                // do a mass update of any languages not yet converted to ISO 639-2 codes
                cleaner.updateLanguages();
                // clean/correct style UUID's on Bookshelves for deleted styles.
                cleaner.bookshelves();

                // check & log, but don't update yet... need more testing
                cleaner.maybeUpdate(true);
            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
            }
            return null;
        }
    }

    /**
     * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
     */
    static class RebuildFtsTask
            extends TaskBase<Void> {

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
                       @NonNull final TaskListener<Void> taskListener) {
            super(taskId, taskListener);
            mDb = db;
        }

        @Override
        @WorkerThread
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("RebuildFtsTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(new TaskProgressMessage(mTaskId,
                                                    R.string.progress_msg_rebuilding_search_index));
            try {
                mDb.rebuildFts();

                PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                                 .edit()
                                 .remove(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED)
                                 .apply();
            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
            }
            return null;
        }
    }

    /**
     * Run 'analyse' on our databases.
     */
    static class AnalyzeDbTask
            extends TaskBase<Void> {

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /**
         * @param taskId       a task identifier, will be returned in the task finished listener.
         * @param db           Database Access
         * @param taskListener for sending progress and finish messages to.
         */
        @UiThread
        AnalyzeDbTask(final int taskId,
                      @NonNull final DAO db,
                      @NonNull final TaskListener<Void> taskListener) {
            super(taskId, taskListener);
            mDb = db;
        }

        @Override
        protected Void doInBackground(final Void... params) {
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
                if (BooklistBuilder.imagesAreCached(App.getAppContext())) {
                    try (CoversDAO cdb = CoversDAO.getInstance()) {
                        cdb.analyze();
                    }
                }

            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
            }
            return null;
        }
    }
}
