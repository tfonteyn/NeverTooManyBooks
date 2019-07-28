package com.eleybourn.bookcatalogue.viewmodels;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.database.CoversDAO;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.database.DBCleaner;
import com.eleybourn.bookcatalogue.database.DBHelper;
import com.eleybourn.bookcatalogue.database.UpgradeDatabase;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;

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
    private final MutableLiveData<String> mTaskProgressMessage = new MutableLiveData<>();

    private final TaskListener<String, Void> mTaskListener = new TaskListener<String, Void>() {
        /**
         * Called when any startup task completes. If no more tasks, let the activity to know.
         *
         * @param taskId a task identifier.
         */
        @UiThread
        @Override
        public void onTaskFinished(final int taskId,
                                   final boolean success,
                                   @Nullable final Void result,
                                   @Nullable final Exception e) {
            synchronized (mAllTasks) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                    Logger.debug(this, "onTaskFinished", "taskId=" + taskId);
                }
                mAllTasks.remove(taskId);
                if (mAllTasks.isEmpty()) {
                    mTaskFinished.setValue(true);
                }
            }
        }

        @UiThread
        @Override
        public void onTaskProgress(final int taskId,
                                   @NonNull final String... values) {
            mTaskProgressMessage.setValue(values[0]);
        }
    };

    /** Database access. */
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
        return startupTasksShouldBeStarted;
    }

    /**
     * We use the standard AsyncTask execute, so tasks are run serially.
     */
    public void startTasks() {
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

            if (App.getPrefs().getBoolean(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED,
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

    private void startTask(@NonNull final StartupTask task) {
        synchronized (mAllTasks) {
            mAllTasks.add(task.getId());
            task.execute();
        }
    }


    /**
     * The tasks here are doing a bit of a special hack.
     * Instead of communicating back via listeners we use the self-reference.
     */
    private abstract static class StartupTask
            extends AsyncTask<Void, String, Void> {

        private final int mTaskId;
        @NonNull
        private final WeakReference<TaskListener<String, Void>> mTaskListener;
        @Nullable
        Exception mException;

        /**
         * Constructor.
         *
         * @param taskId a task identifier, will be returned in the task finished listener.
         */
        @UiThread
        StartupTask(final int taskId,
                    @NonNull final TaskListener<String, Void> taskListener) {
            mTaskId = taskId;
            mTaskListener = new WeakReference<>(taskListener);
        }

        int getId() {
            return mTaskId;
        }

        @Override
        protected void onProgressUpdate(final String... values) {
            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskProgress(mTaskId, values);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onProgressUpdate",
                                 "WeakReference to listener was dead");
                }
            }
        }

        @Override
        @UiThread
        protected void onPostExecute(@Nullable final Void result) {
            if (mTaskListener.get() != null) {
                mTaskListener.get().onTaskFinished(mTaskId, mException == null, result, mException);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 "WeakReference to listener was dead");
                }
            }
        }
    }

    /**
     * Build the dedicated SharedPreferences file with the language mappings.
     * Only build once per Locale.
     */
    static class BuildLanguageMappingsTask
            extends StartupTask {

        /**
         * Constructor.
         *
         * @param taskId a task identifier, will be returned in the task finished listener.
         */
        @UiThread
        BuildLanguageMappingsTask(final int taskId,
                                  @NonNull final TaskListener<String, Void> listener) {
            super(taskId, listener);
        }

        @Override
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("BuildLanguageMappingsTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(App.getAppContext().getString(R.string.progress_msg_upgrading));
            try {
                LocaleUtils.createLanguageMappingCache();

            } catch (@NonNull final RuntimeException e) {
                mException = e;
            }
            return null;
        }
    }

    /**
     * Data cleaning. Done on each startup.
     */
    static class DBCleanerTask
            extends StartupTask {

        /** Database access. */
        @NonNull
        private final DAO mDb;

        /**
         * Constructor.
         *
         * @param taskId a task identifier, will be returned in the task finished listener.
         * @param db     the database
         */
        @UiThread
        DBCleanerTask(final int taskId,
                      @NonNull final DAO db,
                      @NonNull final TaskListener<String, Void> listener) {
            super(taskId, listener);
            mDb = db;
        }

        @WorkerThread
        @Override
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("DBCleanerTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(App.getAppContext().getString(R.string.progress_msg_optimizing));
            try {
                DBCleaner cleaner = new DBCleaner(mDb);

                // do a mass update of any languages not yet converted to ISO 639-2 codes
                cleaner.updateLanguages();
                // clean/correct style UUID's on Bookshelves for deleted styles.
                cleaner.bookshelves();

                // check & log, but don't update yet... need more testing
                cleaner.maybeUpdate(true);
            } catch (@NonNull final RuntimeException e) {
                mException = e;
            }
            return null;
        }
    }

    /**
     * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
     *
     * @author Philip Warner
     */
    static class RebuildFtsTask
            extends StartupTask {

        /** Database access. */
        @NonNull
        private final DAO mDb;

        /**
         * Constructor.
         *
         * @param taskId a task identifier, will be returned in the task finished listener.
         * @param db     the database
         */
        @UiThread
        RebuildFtsTask(final int taskId,
                       @NonNull final DAO db,
                       @NonNull final TaskListener<String, Void> listener) {
            super(taskId, listener);
            mDb = db;
        }

        @Override
        @WorkerThread
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("RebuildFtsTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(
                    App.getAppContext().getString(R.string.progress_msg_rebuilding_search_index));
            try {
                mDb.rebuildFts();

                App.getPrefs()
                   .edit()
                   .remove(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED)
                   .apply();
            } catch (@NonNull final RuntimeException e) {
                mException = e;
            }
            return null;
        }
    }

    /**
     * Run 'analyse' on our databases.
     */
    static class AnalyzeDbTask
            extends StartupTask {

        /** Database access. */
        @NonNull
        private final DAO mDb;

        /**
         * @param taskId a task identifier, will be returned in the task finished listener.
         * @param db     the database
         */
        @UiThread
        AnalyzeDbTask(final int taskId,
                      @NonNull final DAO db,
                      @NonNull final TaskListener<String, Void> listener) {
            super(taskId, listener);
            mDb = db;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            Thread.currentThread().setName("AnalyzeDbTask");

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
                Logger.debug(this, "doInBackground", "taskId=" + getId());
            }
            publishProgress(App.getAppContext().getString(R.string.progress_msg_optimizing));
            try {
                // small hack to make sure we always update the triggers.
                // Makes creating/modifying triggers MUCH easier.
                if (BuildConfig.DEBUG /* always */) {
                    mDb.recreateTriggers();
                }

                mDb.analyze();
                if (BooklistBuilder.imagesAreCached()) {
                    try (CoversDAO cdb = CoversDAO.getInstance()) {
                        cdb.analyze();
                    }
                }

            } catch (@NonNull final RuntimeException e) {
                mException = e;
            }
            return null;
        }
    }
}
