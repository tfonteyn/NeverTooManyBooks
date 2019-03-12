/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue;

import android.Manifest;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.eleybourn.bookcatalogue.backup.ui.BackupAndRestoreActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBCleaner;
import com.eleybourn.bookcatalogue.database.DBHelper;
import com.eleybourn.bookcatalogue.database.UpgradeDatabase;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Single Activity to be the 'Main' activity for the app.
 * It does app-startup stuff which is initially to start the 'real' main activity.
 *
 * @author Philip Warner
 */
public class StartupActivity
        extends AppCompatActivity
        implements ProgressDialogFragment.OnProgressCancelledListener {

    /** the 'LastVersion' i.e. the version which was installed before the current one. */
    public static final String PREF_STARTUP_LAST_VERSION = "Startup.LastVersion";
    /** Number of times the app has been started. */
    public static final String PREF_STARTUP_COUNT = "Startup.StartCount";
    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    public static final String PREFS_STARTUP_COUNTDOWN = "Startup.StartCountdown";
    private static final String TAG = StartupActivity.class.getSimpleName();
    /** Number of app startup's between offers to backup. */
    private static final int PROMPT_WAIT_BACKUP = 5;
    /** Number of app startup's between displaying the Amazon hint. */
    private static final int PROMPT_WAIT_AMAZON = 7;

    /** Indicates the upgrade message has been shown. */
    private static boolean mUpgradeMessageShown;
    /** Flag indicating Amazon hint should be shown. */
    private static boolean mShowAmazonHint;
    /** Flag to ensure tasks are only ever started once (at real startup). */
    private static boolean mStartupTasksShouldBeStarted = true;
    /** Self reference for use during upgrades. */
    private static WeakReference<StartupActivity> mStartupActivity;
    /** TaskId holder. Added when started. Removed when stopped. */
    private final Set<Integer> mAllTasks = new HashSet<>();
    /** Progress Dialog for startup tasks. */
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** Flag indicating a backup is required after startup. */
    private boolean mBackupRequired;
    /** stage the startup is at. */
    private int mStartupStage;
    /** database used by startup tasks. */
    private DBA mDb;

    /**
     * Kludge to get a reference to the currently running StartupActivity, if defined.
     *
     * @return Reference or null.
     */
    @Nullable
    public static StartupActivity getActiveActivity() {
        return mStartupActivity != null ? mStartupActivity.get() : null;
    }

    public static boolean showAmazonHint() {
        return mShowAmazonHint;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // request Permissions (Android 6+)
        if (Build.VERSION.SDK_INT >= 23) {
            initStorage();
        } else {
            // older Android, simply move forward as the permissions will have
            // been requested at install time. The SecurityException will never be thrown
            // but the API requires catching it.
            try {
                int msgId = StorageUtils.initSharedDirectories();
            } catch (SecurityException ignore) {
            }
            startNextStage();
        }
    }

    /**
     * determines the order of startup stages.
     */
    private void startNextStage() {
        // onCreate being stage 0
        mStartupStage++;
        Logger.info(this, "startNextStage","stage=" + mStartupStage);

        switch (mStartupStage) {
            case 1:
                // Create a progress dialog; we may not use it...
                // but we need it to be created in the UI thread.
                openProgressDialog();

                openDBA();
                startTasks();
                break;

            case 2:
                // Get rid of the progress dialog
                closeProgressDialog();
                // tasks are done.
                if (mDb != null) {
                    mDb.close();
                    mDb = null;
                }

                checkForUpgrades();
                break;

            case 3:
                backupRequired();
                break;

            case 4:
                gotoMainScreen();
                break;

            default:
                throw new IllegalStateException();
        }
    }

    private void startTasks() {
        if (!isTaskRoot()) {
            Logger.debug("Startup isTaskRoot() = FALSE");
        }

        if (mStartupTasksShouldBeStarted) {
            mStartupActivity = new WeakReference<>(this);

            updateProgress(getString(R.string.progress_msg_starting_up));

            int taskId = 0;
            new BuildLanguageMappingsTask(++taskId).execute();
            mAllTasks.add(taskId);
            new DBCleanerTask(++taskId, mDb).execute();
            mAllTasks.add(taskId);
            if (Prefs.getPrefs().getBoolean(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED,
                                            false)) {
                new RebuildFtsTask(++taskId, mDb).execute();
                mAllTasks.add(taskId);
            }
            new AnalyzeDbTask(++taskId, mDb).execute();
            mAllTasks.add(taskId);

            // Remove old logs
            Logger.clearLog();
            // Clear the flag
            mStartupTasksShouldBeStarted = false;

            // ENHANCE: add checks for new Events/crashes
        }

        // If no tasks were queued, then move on to next stage. Otherwise, the completed
        // tasks will cause the next stage to start.
        if (mAllTasks.isEmpty()) {
            startNextStage();
        }
    }

    /**
     * Called in UI thread after last startup task completes, or if there are no tasks to queue.
     */
    private void checkForUpgrades() {
        // Remove the weak reference. Only used by db onUpgrade.
        mStartupActivity.clear();
        // Display upgrade message if necessary, otherwise go on to next stage
        if (mUpgradeMessageShown) {
            startNextStage();
            return;
        }
        // Display upgrade message if necessary, otherwise go on to next stage
        String message = UpgradeMessageManager.getUpgradeMessage();
        if (message.isEmpty()) {
            startNextStage();
            return;
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.about_lbl_upgrade)
                .setIcon(R.drawable.ic_info_outline)
                .setMessage(Html.fromHtml(UpgradeMessageManager.getUpgradeMessage()))
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 UpgradeMessageManager.setUpgradeAcknowledged();
                                 startNextStage();
                             }
                         });
        dialog.show();
        mUpgradeMessageShown = true;
    }

    /**
     * If the backup-counter has reached zer, prompt the user to make a backup.
     * <p>
     * Note the backup is not done here; we just set a flag if requested.
     */
    private void backupRequired() {
        mBackupRequired = false;

        if (decreaseStartupCounters()) {
            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.warning_backup_request)
                    .setTitle(R.string.lbl_backup_dialog)
                    .setIcon(R.drawable.ic_help_outline)
                    .create();

            dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                             getString(android.R.string.cancel),
                             new DialogInterface.OnClickListener() {
                                 public void onClick(@NonNull final DialogInterface dialog,
                                                     final int which) {
                                     dialog.dismiss();
                                 }
                             });
            dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                             getString(android.R.string.ok),
                             new DialogInterface.OnClickListener() {
                                 public void onClick(@NonNull final DialogInterface dialog,
                                                     final int which) {
                                     mBackupRequired = true;
                                     dialog.dismiss();
                                 }
                             });
            dialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(@NonNull final DialogInterface dialog) {
                    startNextStage();
                }
            });
            dialog.show();
        } else {
            startNextStage();
        }
    }

    /**
     * Last step: start the main user activity.
     * If requested earlier, run a backup now.
     */
    private void gotoMainScreen() {
        Intent intent = new Intent(this, BooksOnBookshelf.class);
        startActivity(intent);

        if (mBackupRequired) {
            Intent backupIntent = new Intent(this, BackupAndRestoreActivity.class)
                    .putExtra(BackupAndRestoreActivity.BKEY_MODE,
                              BackupAndRestoreActivity.BVAL_MODE_SAVE);
            startActivity(backupIntent);
        }

        // We are done here.
        finish();
    }


    private void openProgressDialog() {
        mProgressDialog = (ProgressDialogFragment)
                getSupportFragmentManager().findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment
                    .newInstance(R.string.progress_msg_starting_up, true, 0);
            mProgressDialog.show(getSupportFragmentManager(), TAG);
            // can't do setMessage right here.. the dialog will not be created yet.
        }
    }

    /**
     * Update the progress dialog, if it has not been dismissed.
     */
    public void updateProgress(@StringRes final int stringId) {
        updateProgress(getString(stringId));
    }

    /**
     * Update the progress dialog, if it has not been dismissed.
     */
    public void updateProgress(@NonNull final String message) {
        // If mProgressDialog is null, it has been dismissed. Don't update.
        if (mProgressDialog == null) {
            return;
        }

        // There is a small chance that this message could be set to display
        // *after* the activity is finished,
        // so we check and we also trap, log and ignore errors.
        // See http://code.google.com/p/android/issues/detail?id=3953
        if (!isFinishing()) {
            try {
                mProgressDialog.setMessage(message);
            } catch (RuntimeException e) {
                Logger.error(e);
            }
        }
    }

    @Override
    public void onProgressCancelled(@Nullable final Integer taskId) {
        // Cancelling the progress dialog cancels the activity.
        finish();
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Decrease and store the number of times the app was opened.
     * Used for proposing Backup/Amazon
     *
     * @return <tt>true</tt> when counter reached 0
     */
    private boolean decreaseStartupCounters() {
        int opened = Prefs.getPrefs().getInt(PREFS_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        int startCount = Prefs.getPrefs().getInt(PREF_STARTUP_COUNT, 0) + 1;

        final SharedPreferences.Editor ed = Prefs.getPrefs().edit();
        if (opened == 0) {
            ed.putInt(PREFS_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        } else {
            ed.putInt(PREFS_STARTUP_COUNTDOWN, opened - 1);
        }
        ed.putInt(PREF_STARTUP_COUNT, startCount);
        ed.apply();

        mShowAmazonHint = (startCount % PROMPT_WAIT_AMAZON) == 0;
        return opened == 0;
    }

    private void initStorage() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    UniqueId.REQ_ANDROID_PERMISSIONS);
            return;
        }
        int msgId = StorageUtils.initSharedDirectories();
        if (msgId != 0) {
            UserMessage.showUserMessage(this, msgId);
        }
        startNextStage();
    }

    private void openDBA() {
        try {
            mDb = new DBA(this);
        } catch (DBHelper.UpgradeException e) {
            Logger.info(this, "openDBA", e.getLocalizedMessage());
            BookCatalogueApp.showNotification(this, R.string.error_unknown,
                                              e.getLocalizedMessage());
            finish();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions which is either
     *                     {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     *                     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}.
     *                     Never null.
     *
     * @see #requestPermissions(String[], int)
     */
    @Override
    @PermissionChecker.PermissionResult
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        //ENHANCE: when/if we request more permissions, then the permissions[] and grantResults[]
        // must be checked in parallel
        switch (requestCode) {
            case UniqueId.REQ_ANDROID_PERMISSIONS:
                if (grantResults.length > 0
                        && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    initStorage();
                } else {
                    // we can't work without Shared Storage, so die; can't use Logger
                    // as we don't have a log file!
                    Log.e("StartupActivity",
                          "No Shared Storage permissions granted, quiting");
                    finishAndRemoveTask();
                }
                break;

            default:
                Logger.error("unknown requestCode=" + requestCode);
                break;
        }
    }

    /**
     * Called in the UI thread when any startup task completes. If no more tasks, start next stage.
     * Because it is in the UI thread, it is not possible for this code to be called until after
     * onCreate() completes, so a race condition is not possible. Equally well, tasks should only
     * be queued in onCreate().
     *
     * @param taskId a task identifier.
     */
    private void onStartupTaskFinished(final int taskId) {
        mAllTasks.remove(taskId);
        if (mAllTasks.isEmpty()) {
            startNextStage();
        }
    }

    private abstract static class StartupTask
            extends AsyncTask<Void, Integer, Void> {

        private final int mTaskId;

        /**
         * Constructor.
         *
         * @param taskId a task identifier, will be returned in the task finished listener.
         */
        @UiThread
        StartupTask(final int taskId) {
            mTaskId = taskId;
        }

        @UiThread
        @Override
        protected void onProgressUpdate(@StringRes final Integer... values) {
            StartupActivity a = StartupActivity.getActiveActivity();
            if (a != null) {
                a.updateProgress(values[0]);
            }
        }

        @UiThread
        @Override
        protected void onPostExecute(final Void result) {
            StartupActivity a = StartupActivity.getActiveActivity();
            if (a != null) {
                a.onStartupTaskFinished(mTaskId);
            }
        }
    }

    /**
     * Run 'analyse' on our databases.
     */
    static class AnalyzeDbTask
            extends StartupTask {

        @NonNull
        private final DBA mDb;

        /**
         * @param taskId a task identifier, will be returned in the task finished listener.
         * @param db     the database
         */
        @UiThread
        AnalyzeDbTask(final int taskId,
                      @NonNull final DBA db) {
            super(taskId);
            mDb = db;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            publishProgress(R.string.progress_msg_optimizing_databases);

            mDb.analyze();
            // small hack to make sure we always update the triggers.
            // Makes creating/modifying triggers MUCH easier.
            if (BuildConfig.DEBUG) {
                mDb.recreateTriggers();
            }

            if (Prefs.getPrefs().getBoolean(UpgradeDatabase.V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED,
                                            false)) {
                UpgradeDatabase.v74_fixupAuthorsAndSeries(mDb);
                Prefs.getPrefs()
                     .edit()
                     .remove(UpgradeDatabase.V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED)
                     .apply();
            }

            if (BooklistBuilder.imagesAreCached()) {
                try (CoversDBA cdb = CoversDBA.getInstance()) {
                    cdb.analyze();
                }
            }

            return null;
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
        BuildLanguageMappingsTask(final int taskId) {
            super(taskId);
        }

        @Override
        protected Void doInBackground(final Void... params) {
            publishProgress(R.string.progress_msg_updating_languages);

            // generate initial language2iso mappings.
            LocaleUtils.createLanguageMappingCache(LocaleUtils.getSystemLocal());
            // the one the user has configured our app into using (set at earliest app startup code)
            LocaleUtils.createLanguageMappingCache(Locale.getDefault());
            // and English
            LocaleUtils.createLanguageMappingCache(Locale.ENGLISH);

            return null;
        }
    }

    /**
     * Data cleaning. This is done each startup. TODO: is that needed ?
     */
    static class DBCleanerTask
            extends StartupTask {

        @NonNull
        private final DBA mDb;

        /**
         * Constructor.
         *
         * @param taskId a task identifier, will be returned in the task finished listener.
         * @param db     the database
         */
        @UiThread
        DBCleanerTask(final int taskId,
                      @NonNull final DBA db) {
            super(taskId);
            mDb = db;
        }

        @WorkerThread
        @Override
        protected Void doInBackground(final Void... params) {
            publishProgress(R.string.progress_msg_cleaning_database);
            /*
             * do a mass update of any languages not yet converted to ISO3 codes
             */
            List<String> names = mDb.getLanguageCodes();
            for (String name : names) {
                if (name != null && name.length() > 3) {
                    String iso = LocaleUtils.getISO3Language(name);
                    Logger.info(this, "doInBackground",
                                "Global language update of `" + name + "` to `" + iso + '`');
                    if (!iso.equals(name)) {
                        mDb.updateLanguage(name, iso);
                    }
                }
            }

            DBCleaner cleaner = new DBCleaner(mDb);
            cleaner.all(true);

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

        @NonNull
        private final DBA mDb;

        /**
         * Constructor.
         *
         * @param taskId a task identifier, will be returned in the task finished listener.
         * @param db     the database
         */
        @UiThread
        RebuildFtsTask(final int taskId,
                       @NonNull final DBA db) {
            super(taskId);
            mDb = db;
        }

        @Override
        @WorkerThread
        protected Void doInBackground(final Void... params) {
            publishProgress(R.string.progress_msg_rebuilding_search_index);

            mDb.rebuildFts();

            Prefs.getPrefs()
                 .edit()
                 .putBoolean(UpgradeDatabase.PREF_STARTUP_FTS_REBUILD_REQUIRED, false)
                 .apply();

            return null;
        }

    }
}
