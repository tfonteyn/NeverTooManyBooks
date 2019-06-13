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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.lifecycle.ViewModelProviders;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.backup.ui.BackupActivity;
import com.eleybourn.bookcatalogue.database.DBHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UpgradeMessageManager;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.viewmodels.StartupViewModel;

/**
 * Single Activity to be the 'Main' activity for the app.
 * It does app-startup stuff which is initially to start the 'real' main activity.
 *
 * @author Philip Warner
 */
public class StartupActivity
        extends AppCompatActivity {

    /** the 'LastVersion' i.e. the version which was installed before the current one. */
    public static final String PREF_STARTUP_LAST_VERSION = "Startup.LastVersion";
    /** Number of times the app has been started. */
    public static final String PREF_STARTUP_COUNT = "Startup.StartCount";
    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    public static final String PREF_STARTUP_COUNTDOWN = "Startup.StartCountdown";
    /** Number of app startup's between offers to backup. */
    private static final int PROMPT_WAIT_BACKUP = 50;
    /** Number of app startup's between displaying the Amazon hint. */
    private static final int PROMPT_WAIT_AMAZON = 70;

    /** Indicates the upgrade message has been shown. */
    private static boolean sUpgradeMessageShown;
    /** Flag indicating Amazon hint should be shown. */
    private static boolean sShowAmazonHint;

    /** Self reference for use by tasks and during upgrades. */
    private static WeakReference<StartupActivity> sStartupActivity;

    /** Flag indicating a backup is required after startup. */
    private boolean mBackupRequired;
    /** stage the startup is at. */
    private int mStartupStage;

    private TextView mProgressMessageView;

    private StartupViewModel mModel;

    /**
     * Kludge to allow the {@link DBHelper} to get a reference to the currently running
     * StartupActivity, so it can send progress messages to the local ProgressDialogFragment.
     *
     * @return Reference or {@code null}.
     */
    @Nullable
    public static StartupActivity getActiveActivity() {
        return sStartupActivity != null ? sStartupActivity.get() : null;
    }

    public static boolean showAmazonHint() {
        return sShowAmazonHint;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocaleUtils.applyPreferred(this);

        // the UI
        setContentView(R.layout.activity_startup);
        mProgressMessageView = findViewById(R.id.progressMessage);

        // create self-reference for DBHelper callbacks.
        sStartupActivity = new WeakReference<>(this);

        // https://developer.android.com/reference/android/os/StrictMode
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                               .detectAll()
                                               .penaltyLog()
                                               .build());
        }

        // get our ViewModel; we init in mStartupStage == 1.
        mModel = ViewModelProviders.of(this).get(StartupViewModel.class);

        // running on Android 6+ -> request Permissions
        if (Build.VERSION.SDK_INT >= 23) {
            if (!initStorage()) {
                // we asked for permissions. TBC in onRequestPermissionsResult
                return;
            }
        } else {
            // older Android, simply move forward as the permissions will have
            // been requested at install time. The SecurityException will never be thrown
            // but the API requires catching it.
            try {
                int msgId = StorageUtils.initSharedDirectories();
                if (msgId != 0) {
                    UserMessage.showUserMessage(this, msgId);
                }
            } catch (SecurityException ignore) {
            }
        }

        startNextStage();
    }

    /**
     * Startup stages.
     */
    private void startNextStage() {
        // onCreate being stage 0
        mStartupStage++;

        if (BuildConfig.DEBUG /* always */) {
            Logger.debug(this, "startNextStage", "stage=" + mStartupStage);
        }

        switch (mStartupStage) {
            case 1:
                startTasks();
                break;

            case 2:
                checkForUpgrades();
                break;

            case 3:
                backupRequired();
                break;

            case 4:
                gotoMainScreen();
                break;

            default:
                throw new IllegalStateException("stage=" + mStartupStage);
        }
    }

    private void startTasks() {
        if (mModel.isStartupTasksShouldBeStarted()) {
            // listen for progress messages
            mModel.getTaskProgressMessage().observe(this, message ->
                    mProgressMessageView.setText(message));

            // when tasks are done, move on to next startup-stage
            mModel.getTaskFinished().observe(this, finished -> {
                if (finished) {
                    startNextStage();
                }
            });

            // any error, notify user and die.
            mModel.getTaskException().observe(this, e -> {
                if (e != null) {
                    App.showNotification(this, R.string.error_unknown,
                                         e.getLocalizedMessage());
                    finish();
                }
            });

            mModel.startTasks();

        } else {
            startNextStage();
        }
    }

    /**
     * Called in UI thread after last startup task completes, or if there are no tasks to queue.
     */
    private void checkForUpgrades() {
        // If upgrade message already shown, go on to next stage
        if (sUpgradeMessageShown) {
            startNextStage();
            return;
        }
        // Display upgrade message if necessary, otherwise go on to next stage
        String upgradeMessage = UpgradeMessageManager.getUpgradeMessage(this);
        if (upgradeMessage.isEmpty()) {
            startNextStage();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.about_lbl_upgrade)
                .setIcon(R.drawable.ic_info_outline)
                .setMessage(Html.fromHtml(upgradeMessage))
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    UpgradeMessageManager.setUpgradeAcknowledged();
                    startNextStage();
                })
                .create()
                .show();

        sUpgradeMessageShown = true;
    }

    /**
     * If the backup-counter has reached zer, prompt the user to make a backup.
     * <p>
     * Note the backup is not done here; we just set a flag if requested.
     */
    private void backupRequired() {
        mBackupRequired = false;

        if (decreaseStartupCounters()) {
            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_help_outline)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.warning_backup_request)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        d.dismiss();
                        mBackupRequired = true;
                    })
                    .setOnDismissListener(d -> startNextStage())
                    .create()
                    .show();
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
            Intent backupIntent = new Intent(this, BackupActivity.class);
            startActivity(backupIntent);
        }

        // We are done here.
        // Remove the weak self-reference and finish.
        sStartupActivity.clear();
        finish();
    }

    /**
     * Decrease and store the number of times the app was opened.
     * Used for proposing Backup/Amazon
     *
     * @return {@code true} when counter reached 0
     */
    private boolean decreaseStartupCounters() {
        int opened = App.getPrefs().getInt(PREF_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        int startCount = App.getPrefs().getInt(PREF_STARTUP_COUNT, 0) + 1;

        final SharedPreferences.Editor ed = App.getPrefs().edit();
        if (opened == 0) {
            ed.putInt(PREF_STARTUP_COUNTDOWN, PROMPT_WAIT_BACKUP);
        } else {
            ed.putInt(PREF_STARTUP_COUNTDOWN, opened - 1);
        }
        ed.putInt(PREF_STARTUP_COUNT, startCount)
          .apply();

        // every so often, let the Amazon hint show.
        sShowAmazonHint = (startCount % PROMPT_WAIT_AMAZON) == 0;

        return opened == 0;
    }

    /**
     * Checks if we have the needed permissions.
     * If we do, initialises storage.
     *
     * @return {@code true} if we had permission and storage is initialized.
     */
    private boolean initStorage() {
        int p = ContextCompat.checkSelfPermission(this,
                                                  Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (p != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    UniqueId.REQ_ANDROID_PERMISSIONS);
            return false;
        }

        int msgId = StorageUtils.initSharedDirectories();
        if (msgId != 0) {
            UserMessage.showUserMessage(this, msgId);
        }

        return true;
    }

    @Override
    @PermissionChecker.PermissionResult
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        // when/if we request more permissions, then permissions[] and grantResults[]
        // must be checked in parallel obviously.

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_ANDROID_PERMISSIONS:
                if (grantResults.length > 0
                        && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    if (initStorage()) {
                        startNextStage();
                    }
                } else {
                    // we can't work without Shared Storage, so die;
                    // and can't use Logger as we don't have a log file!
                    Log.e("StartupActivity", "No Shared Storage permissions granted, quiting");
                    finishAndRemoveTask();
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "unknown requestCode=" + requestCode);
                break;
        }
    }

    /**
     * Use by the DB upgrade procedure.
     *
     * @param messageId to display
     */
    public void onProgress(@StringRes final int messageId) {
        mProgressMessageView.setText(messageId);
    }
}
