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
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;

import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.database.UpgradeDatabase;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.OnTaskFinishListener;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTask;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueue.SimpleTaskContext;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Single Activity to be the 'Main' activity for the app. It does app-startup stuff which is initially
 * to start the 'real' main activity.
 *
 * @author Philip Warner
 */
public class StartupActivity extends AppCompatActivity {

    private static final String TAG = "StartupActivity";

    /** Options to indicate FTS rebuild is required at startup */
    private static final String PREF_FTS_REBUILD_REQUIRED = TAG + ".FtsRebuildRequired";
    // obsolete from v74
    @Deprecated
    public static final String PREF_AUTHOR_SERIES_FIX_UP_REQUIRED = TAG + ".FAuthorSeriesFixupRequired";

    private static final String PREFS_STATE_OPENED = "state_opened";
    /** Number of times the app has been started */
    private static final String PREF_START_COUNT = "Startup.StartCount";

    /** Number of app startup's between offers to backup */
    private static final int BACKUP_PROMPT_WAIT = 5;

    /** Number of app startup's between displaying the Amazon hint */
    private static final int AMAZON_PROMPT_WAIT = 7;

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
    private SimpleTaskQueue mTaskQueue = null;
    /** Progress Dialog for startup tasks
     *  ENHANCE: this is a global requirement: ProgressDialog is deprecated in API 26
     *  https://developer.android.com/reference/android/app/ProgressDialog
     *  Suggested: ProgressBar or Notification.
     *  Alternative maybe: SnackBar (recommended replacement for Toast)
     */
    @Deprecated
    private ProgressDialog mProgress = null;
    /** Options indicating an export is required after startup */
    private boolean mExportRequired = false;
    /** UI thread */
    private Thread mUiThread;
    /** stage the startup is at. */
    private int mStartupStage = 0;

    /** Set the flag to indicate an FTS rebuild is required */
    public static void scheduleFtsRebuild() {
        BCPreferences.setBoolean(PREF_FTS_REBUILD_REQUIRED, true);
    }

    /**
     * Kludge to get a reference to the currently running StartupActivity, if defined.
     *
     * @return Reference or null.
     */
    @Nullable
    public static StartupActivity getActiveActivity() {
        if (mStartupActivity != null) {
            return mStartupActivity.get();
        } else {
            return null;
        }
    }

    public static boolean getShowAmazonHint() {
        return mShowAmazonHint;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // request Permissions (Android 6+) ENHANCE
        if (Build.VERSION.SDK_INT >= 23) {
            checkPermissions();
        } else {
            // older Android, simply move forward as the permissions will have
            // been requested at install time.
            startNextStage();
        }
    }

    /** determines the order of startup stages (without having to manually renumber */
    private void startNextStage() {
        // onCreate being stage 0
        mStartupStage++;
        System.out.println("Starting stage " + mStartupStage);

        switch (mStartupStage) {
            case 1: {
                // Create a progress dialog; we may not use it...but we need it to be created in the UI thread.
                mProgress = ProgressDialog.show(this,
                        getString(R.string.book_catalogue_startup),
                        getString(R.string.starting_up),
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
                proposeBackup();
                break;
            case 4:
                gotoMainScreen();
                break;
        }
    }

    private void startTasks() throws SecurityException {
        if (BuildConfig.DEBUG) {
            System.out.println("Startup isTaskRoot() = " + isTaskRoot());
        }

        mUiThread = Thread.currentThread();

        // If it's a real application startup...cleanup old stuff
        if (mIsReallyStartup) {
            mStartupActivity = new WeakReference<>(this);

            updateProgress(R.string.starting);

            // at this point the user should have granted us STORAGE permission,
            // so make sure we have our directories ready
            StorageUtils.initSharedDirectories();

            SimpleTaskQueue q = getQueue();

            // Always enqueue it; it will get a DB and check if required...
            q.enqueue(new RebuildFtsTask());
            q.enqueue(new AnalyzeDbTask());

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
    public void updateProgress(@StringRes final int stringId) {
        updateProgress(getString(stringId));
    }

    /**
     * Update the progress dialog, if it has not been dismissed.
     */
    private void updateProgress(@NonNull final String message) {
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
                    Logger.logError(e);
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
    private void taskCompleted(SimpleTask task) {
        if (BuildConfig.DEBUG) {
            System.out.println("Task Completed: " + task.getClass().getSimpleName());
        }
        if (!mTaskQueue.hasActiveTasks()) {
            if (BuildConfig.DEBUG) {
                System.out.println("Task Completed - no more");
            }
            startNextStage();
        }
    }


    /**
     * Get (or create) the task queue.
     */
    private SimpleTaskQueue getQueue() {
        if (mTaskQueue == null) {
            mTaskQueue = new SimpleTaskQueue("startup-tasks", 1);
            // Listen for task completions
            mTaskQueue.setTaskFinishListener(new OnTaskFinishListener() {
                @Override
                public void onTaskFinish(@NonNull final SimpleTask task, @Nullable final Exception e) {
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
        if (mUpgradeMessageShown || UpgradeMessageManager.getUpgradeMessage().isEmpty()) {
            startNextStage();
        } else {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setMessage(Html.fromHtml(UpgradeMessageManager.getUpgradeMessage()))
                    .setTitle(R.string.upgrade_title)
                    .setIcon(R.drawable.ic_info_outline)
                    .create();

            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            UpgradeMessageManager.setMessageAcknowledged();
                            startNextStage();
                        }
                    });
            dialog.show();
            mUpgradeMessageShown = true;
        }
    }

    private void proposeBackup() {
        int opened = BCPreferences.getInt(PREFS_STATE_OPENED, BACKUP_PROMPT_WAIT);
        int startCount = BCPreferences.getInt(PREF_START_COUNT, 0) + 1;

        Editor ed = BCPreferences.edit();
        if (opened == 0) {
            ed.putInt(PREFS_STATE_OPENED, BACKUP_PROMPT_WAIT);
        } else {
            ed.putInt(PREFS_STATE_OPENED, opened - 1);
        }
        ed.putInt(PREF_START_COUNT, startCount);
        ed.commit();

        mShowAmazonHint = ((startCount % AMAZON_PROMPT_WAIT) == 0);

        mExportRequired = false;

        if (opened == 0) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setMessage(R.string.backup_request)
                    .setTitle(R.string.backup_title)
                    .setIcon(R.drawable.ic_help_outline)
                    .create();

            dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                    getString(android.R.string.cancel)
                    , new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    });
            dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                    getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int which) {
                            mExportRequired = true;
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
     * Last step
     */
    private void gotoMainScreen() {
        Intent intent = new Intent(this, BooksOnBookshelf.class);
        startActivity(intent);

        if (mExportRequired) {
            AdministrationFunctions.exportToArchive(this);
        }

        // We are done
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTaskQueue != null) {
            mTaskQueue.finish();
        }
    }

    /**
     * Minimally needed.
     * - WRITE_EXTERNAL_STORAGE
     *
     * Other permissions we use will be requested when needed
     * - READ_CONTACTS
     */
    @NonNull
    private String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }

    /**
     * See if we now have all of the required dangerous permissions. Otherwise, tell the user that
     * they can't continue without granting the permissions, and then request the permissions again.
     */
    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String permissions[],
                                           @NonNull final int[] grantResults) {
        if (requestCode == UniqueId.ACTIVITY_REQUEST_CODE_PERMISSIONS_REQUEST) {
            checkPermissions();
        }
    }

    private void checkPermissions() {
        // Convert the array of required permissions to a {@link Set} to remove redundant elements.
        // Then remove already granted permissions, and return an array of missing permissions.
        Set<String> permissions = new HashSet<>();
        Collections.addAll(permissions, getRequiredPermissions());

        for (Iterator<String> i = permissions.iterator(); i.hasNext(); ) {
            //ENHANCE: when going to API 23+, use native call
            if (ContextCompat.checkSelfPermission(this, i.next()) == PackageManager.PERMISSION_GRANTED) {
                i.remove();
            }
        }
        String[] missing = permissions.toArray(new String[0]);

        if (missing.length == 0) {
            startNextStage();
        } else {
            ActivityCompat.requestPermissions(this, missing, UniqueId.ACTIVITY_REQUEST_CODE_PERMISSIONS_REQUEST);
            //ENHANCE: when going to API 23+, use native call
            //requestPermissions(missing, UniqueId.ACTIVITY_REQUEST_CODE_PERMISSIONS_REQUEST);
        }
    }

    /**
     * Task to rebuild FTS in background. Can take several seconds, so not done in onUpgrade().
     *
     * @author Philip Warner
     */
    public class RebuildFtsTask implements SimpleTask {

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {
            // Get a DB to make sure the FTS rebuild flag is set appropriately
            CatalogueDBAdapter db = taskContext.getDb();
            if (BCPreferences.getBoolean(PREF_FTS_REBUILD_REQUIRED, false)) {
                updateProgress(R.string.rebuilding_search_index);
                db.rebuildFts();
                BCPreferences.setBoolean(PREF_FTS_REBUILD_REQUIRED, false);
            }
        }

        @Override
        public void onFinish(Exception e) {
        }

    }

    public class AnalyzeDbTask implements SimpleTask {

        @Override
        public void run(@NonNull final SimpleTaskContext taskContext) {

            CatalogueDBAdapter db = taskContext.getDb();

            updateProgress(R.string.optimizing_databases);
            db.analyzeDb();
            if (BooklistPreferencesActivity.isThumbnailCacheEnabled()) {
                try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(StartupActivity.this)) {
                    coversDbHelper.analyze();
                }
            }

            if (BCPreferences.getBoolean(PREF_AUTHOR_SERIES_FIX_UP_REQUIRED, false)) {
                UpgradeDatabase.v74_fixupAuthorsAndSeries(db);
                BCPreferences.remove(PREF_AUTHOR_SERIES_FIX_UP_REQUIRED);
            }
        }

        @Override
        public void onFinish(Exception e) {
        }

    }
}
