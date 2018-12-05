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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;

import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.CoversDBAdapter;
import com.eleybourn.bookcatalogue.database.DBCleaner;
import com.eleybourn.bookcatalogue.database.UpgradeDatabase;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.backup.ui.BackupAndRestoreActivity;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.OnTaskFinishListener;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

/**
 * Single Activity to be the 'Main' activity for the app. It does app-startup stuff which is initially
 * to start the 'real' main activity.
 *
 * @author Philip Warner
 */
public class StartupActivity extends AppCompatActivity {

    public static final String TAG = "Startup.";
    /** the 'LastVersion' e.g. the version which was installed before the current one. */
    public final static String PREF_STARTUP_LAST_VERSION = TAG + "LastVersion";
    /** Number of times the app has been started */
    public static final String PREF_STARTUP_COUNT = TAG + "StartCount";
    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    public static final String PREFS_STARTUP_COUNTDOWN =  TAG + "StartCountdown";
    /** Number of app startup's between offers to backup */
    private static final int PROMPT_WAIT_BACKUP = 5;
    /** Number of app startup's between displaying the Amazon hint */
    private static final int PROMPT_WAIT_AMAZON = 7;

    /** Options to indicate FTS rebuild is required at startup */
    private static final String PREF_STARTUP_FTS_REBUILD_REQUIRED = TAG + "FtsRebuildRequired";

    /** Indicates the upgrade message has been shown */
    private static boolean mUpgradeMessageShown = false;
    /** Options set to true on first call */
    private static boolean mIsReallyStartup = true;
    /** Options indicating Amazon hint could be shown */
    private static boolean mShowAmazonHint = false;
    private static WeakReference<StartupActivity> mStartupActivity = null;
    /** Handler to post run'ables to UI thread */
    private final Handler mHandler = new Handler();
    /** Queue for executing startup tasks, if any */
    @Nullable
    private SimpleTaskQueue mTaskQueue = null;
    /**
     * Progress Dialog for startup tasks
     * API_UPGRADE 26 this is a global requirement: ProgressDialog is deprecated
     * https://developer.android.com/reference/android/app/ProgressDialog
     * Suggested: ProgressBar or Notification.
     * Alternative maybe: SnackBar (recommended replacement for Toast)
     */
    @Nullable
    @Deprecated
    private ProgressDialog mProgress = null;

    /** Options indicating a backup is required after startup */
    private boolean mBackupRequired = false;
    /** UI thread */
    private Thread mUiThread;
    /** stage the startup is at. */
    private int mStartupStage = 0;

    /** Set the flag to indicate an FTS rebuild is required */
    public static void scheduleFtsRebuild() {
        BookCatalogueApp.getSharedPreferences().edit().putBoolean(PREF_STARTUP_FTS_REBUILD_REQUIRED, true).apply();
    }

    /**
     * Kludge to get a reference to the currently running StartupActivity, if defined.
     *
     * @return Reference or null.
     */
    @Nullable
    public static StartupActivity getActiveActivity() {
        return mStartupActivity != null ? mStartupActivity.get() : null;
    }

    public static boolean getShowAmazonHint() {
        return mShowAmazonHint;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        // request Permissions (Android 6+)
        if (Build.VERSION.SDK_INT >= 23) {
            initStorage();
        } else {
            // older Android, simply move forward as the permissions will have
            // been requested at install time. The SecurityException will never be thrown
            // but the API requires catching it.
            try {
                StorageUtils.initSharedDirectories();
            } catch (SecurityException ignore) {
            }
            startNextStage();
        }
        Tracker.exitOnCreate(this);
    }

    private void initStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_PERMISSIONS_REQUEST);
            return;
        }
        StorageUtils.initSharedDirectories();
        startNextStage();
    }

    /** determines the order of startup stages */
    private void startNextStage() {
        // onCreate being stage 0
        mStartupStage++;
        Logger.info(this, "Starting stage " + mStartupStage);

        switch (mStartupStage) {
            case 1: {
                // Create a progress dialog; we may not use it...but we need it to be created in the UI thread.
                mProgress = ProgressDialog.show(this,
                        getString(R.string.lbl_book_catalogue_startup),
                        getString(R.string.progress_msg_starting_up),
                        true,
                        true, new OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                // Cancelling the list cancels the activity.
                                StartupActivity.this.finish();
                            }
                        });
                startTasks();
                break;
            }
            case 2: {
                // Get rid of the progress dialog
                if (mProgress != null) {
                    mProgress.dismiss();
                    mProgress = null;
                }
                checkForUpgrades();
                break;
            }
            case 3:
                backupRequired();
                break;
            case 4:
                gotoMainScreen();
                break;
        }
    }

    private void startTasks() {
        if (!isTaskRoot()) {
            Logger.debug("Startup isTaskRoot() = FALSE");
        }

        mUiThread = Thread.currentThread();

        if (mIsReallyStartup) {
            mStartupActivity = new WeakReference<>(this);

            updateProgress(R.string.starting);

            SimpleTaskQueue q = getQueue();
            q.enqueue(new RebuildFtsTask());
            q.enqueue(new BuildLanguageMappingsTask());
            q.enqueue(new AnalyzeDbTask());
            q.enqueue(new DBCleanerTask());

            // Remove old logs
            Logger.clearLog();
            // Clear the flag
            mIsReallyStartup = false;

            // ENHANCE: add checks for new Events/crashes
        }

        // If no tasks were queued, then move on to next stage. Otherwise, the completed
        // tasks will cause the next stage to start.
        if (mTaskQueue == null) {
            startNextStage();
        }
    }

    /**
     * Update the progress dialog, if it has not been dismissed.
     */
    public void updateProgress(final @StringRes int stringId) {
        updateProgress(getString(stringId));
    }

    /**
     * Update the progress dialog, if it has not been dismissed.
     */
    private void updateProgress(final @NonNull String message) {
        // If mProgress is null, it has been dismissed. Don't update.
        if (mProgress == null) {
            return;
        }

        // If we are in the UI thread, update the progress.
        if (Thread.currentThread().equals(mUiThread)) {
            // There is a small chance that this message could be set to display *after* the activity is finished,
            // so we check and we also trap, log and ignore errors.
            // See http://code.google.com/p/android/issues/detail?id=3953
            if (!isFinishing()) {
                try {
                    mProgress.setMessage(message);
                    if (!mProgress.isShowing()) {
                        mProgress.show();
                    }
                } catch (Exception e) {
                    Logger.error(e);
                }
            }
        } else {
            // If we are NOT in the UI thread, queue it to the UI thread.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateProgress(message);
                }
            });
        }
    }

    /**
     * Called in the UI thread when any startup task completes. If no more tasks, start stage 2.
     * Because it is in the UI thread, it is not possible for this code to be called until after
     * onCreate() completes, so a race condition is not possible. Equally well, tasks should only
     * be queued in onCreate().
     */
    private void taskCompleted(@NonNull SimpleTaskQueue.SimpleTask task) {
        if (DEBUG_SWITCHES.STARTUP && BuildConfig.DEBUG) {
            Logger.info(task, "Task Completed");
        }
        if (!getQueue().hasActiveTasks()) {
            if (DEBUG_SWITCHES.STARTUP && BuildConfig.DEBUG) {
                Logger.info(this, "Task Completed - all done");
            }
            startNextStage();
        }
    }

    /**
     * Get (or create) the task queue.
     */
    @NonNull
    private SimpleTaskQueue getQueue() {
        if (mTaskQueue == null) {
            mTaskQueue = new SimpleTaskQueue("StartupActivity-tasks", 1);
            // Listen for task completions
            mTaskQueue.setTaskFinishListener(new OnTaskFinishListener() {
                @Override
                public void onTaskFinish(final @NonNull SimpleTaskQueue.SimpleTask task, final @Nullable Exception e) {
                    taskCompleted(task);
                }
            });
        }
        return mTaskQueue;
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

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(Html.fromHtml(UpgradeMessageManager.getUpgradeMessage()))
                .setTitle(R.string.about_lbl_upgrade)
                .setIcon(R.drawable.ic_info_outline)
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        UpgradeMessageManager.setUpgradeAcknowledged();
                        startNextStage();
                    }
                });
        dialog.show();
        mUpgradeMessageShown = true;
    }

    private void backupRequired() {
        mBackupRequired = false;

        if (proposeBackup()) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.backup_request)
                    .setTitle(R.string.lbl_backup_dialog)
                    .setIcon(R.drawable.ic_help_outline)
                    .create();

            dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                    getString(android.R.string.cancel)
                    , new DialogInterface.OnClickListener() {
                        public void onClick(final @NonNull DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    });
            dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                    getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final @NonNull DialogInterface dialog, final int which) {
                            mBackupRequired = true;
                            dialog.dismiss();
                        }
                    });
            dialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    startNextStage();
                }
            });
            dialog.show();
        } else {
            startNextStage();
        }
    }

    /**
     * Decrease and store the number of times the app was opened.
     * Used for proposing Backup/Amazon
     *
     * @return true when counter reached 0
     */
    private boolean proposeBackup() {
        int opened = BookCatalogueApp.getIntPreference(PREFS_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        int startCount = BookCatalogueApp.getIntPreference(PREF_STARTUP_COUNT, 0) + 1;

        final SharedPreferences.Editor ed = BookCatalogueApp.getSharedPreferences().edit();
        if (opened == 0) {
            ed.putInt(PREFS_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        } else {
            ed.putInt(PREFS_STARTUP_COUNTDOWN, opened - 1);
        }
        ed.putInt(PREF_STARTUP_COUNT, startCount);
        ed.apply();

        mShowAmazonHint = ((startCount % PROMPT_WAIT_AMAZON) == 0);
        return opened == 0;
    }

    /**
     * Last step
     */
    private void gotoMainScreen() {
        Intent intent = new Intent(this, BooksOnBookshelf.class);
        startActivity(intent);

        if (mBackupRequired) {
            Intent backupIntent = new Intent(this, BackupAndRestoreActivity.class);
            backupIntent.putExtra(BackupAndRestoreActivity.BKEY_MODE, BackupAndRestoreActivity.BVAL_MODE_SAVE);
            startActivity(backupIntent);
        }

        // We are done
        finish();
    }

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        if (mTaskQueue != null) {
            mTaskQueue.terminate();
        }
        super.onDestroy();
        Tracker.exitOnDestroy(this);
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
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     *                     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
     *
     * @see #requestPermissions(String[], int)
     */
    @Override
    @PermissionChecker.PermissionResult
    public void onRequestPermissionsResult(final int requestCode, final @NonNull String permissions[], final @NonNull int[] grantResults) {
        //ENHANCE: when/if we request more permissions, then the permissions[] and grantResults[] must be checked in parallel
        switch (requestCode) {
            case UniqueId.ACTIVITY_REQUEST_CODE_ANDROID_PERMISSIONS_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initStorage();
                } else {
                    // we can't work without storage, so die; can't use Logger as we don't have a log file!
                    Log.e("StartupActivity","No storage permissions granted, quiting");
                    finishAndRemoveTask();
                }
            }
        }
    }


    /**
     * Build the dedicated SharedPreferences file with the language mappings.
     * Only build once per Locale.
     */
    public class BuildLanguageMappingsTask implements SimpleTaskQueue.SimpleTask {
        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            updateProgress(R.string.progress_msg_updating_languages);

            // generate initial language2iso mappings.
            LocaleUtils.createLanguageMappingCache(LocaleUtils.getSystemLocal());
            // the one the user has configured our app into using
            LocaleUtils.createLanguageMappingCache(Locale.getDefault());
            // and English
            LocaleUtils.createLanguageMappingCache(Locale.ENGLISH);
        }

        @Override
        public void onFinish(Exception e) {
        }
    }


    /**
     * Data cleaning. This is done each startup. TODO: is that needed ?
     */
    public class DBCleanerTask implements SimpleTaskQueue.SimpleTask {
        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            updateProgress(R.string.progress_msg_cleaning_database);

            // Get a DB, do not close the database!
            CatalogueDBAdapter db = taskContext.getDb();

            /*
             * do a mass update of any languages not yet converted to ISO3 codes
             */
            List<String> names = db.getLanguageCodes();
            for (String name : names) {
                if (name != null && name.length() > 3) {
                    String iso = LocaleUtils.getISO3Language(name);
                    Logger.info(this, "Global language update of `" + name + "` to `" + iso + "`");
                    if (!iso.equals(name)) {
                        db.globalReplaceLanguage(name, iso);
                    }
                }
            }


            DBCleaner cleaner = new DBCleaner(db);
            cleaner.all(true);
        }

        @Override
        public void onFinish(Exception e) {
        }
    }

    /**
     * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
     *
     * @author Philip Warner
     */
    public class RebuildFtsTask implements SimpleTaskQueue.SimpleTask {

        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            // Get a DB to make sure the FTS rebuild flag is set appropriately, do not close the database!
            CatalogueDBAdapter db = taskContext.getDb();
            if (BookCatalogueApp.getBooleanPreference(PREF_STARTUP_FTS_REBUILD_REQUIRED, false)) {
                updateProgress(R.string.progress_msg_rebuilding_search_index);
                db.rebuildFts();
                BookCatalogueApp.getSharedPreferences().edit().putBoolean(PREF_STARTUP_FTS_REBUILD_REQUIRED, false).apply();
            }
        }

        @Override
        public void onFinish(Exception e) {
        }
    }

    public class AnalyzeDbTask implements SimpleTaskQueue.SimpleTask {

        @Override
        public void run(final @NonNull SimpleTaskContext taskContext) {
            updateProgress(R.string.progress_msg_optimizing_databases);

            // Get a connection, do not close the databases!
            CatalogueDBAdapter db = taskContext.getDb();
            db.analyzeDb();

            if (BooklistPreferencesActivity.thumbnailsAreCached()) {
                CoversDBAdapter coversDBAdapter = taskContext.getCoversDb();
                coversDBAdapter.analyze();
            }

            if (BookCatalogueApp.getBooleanPreference(UpgradeDatabase.V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, false)) {
                UpgradeDatabase.v74_fixupAuthorsAndSeries(db);
                BookCatalogueApp.getSharedPreferences().edit().remove(UpgradeDatabase.V74_PREF_AUTHOR_SERIES_FIX_UP_REQUIRED).apply();
            }
        }

        @Override
        public void onFinish(Exception e) {
        }

    }
}
