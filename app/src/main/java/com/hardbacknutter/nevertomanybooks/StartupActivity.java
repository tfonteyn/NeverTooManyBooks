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
package com.hardbacknutter.nevertomanybooks;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.lifecycle.ViewModelProvider;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertomanybooks.backup.ui.BackupActivity;
import com.hardbacknutter.nevertomanybooks.database.DBHelper;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertomanybooks.utils.UpgradeMessageManager;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertomanybooks.viewmodels.StartupViewModel;

/**
 * Single Activity to be the 'Main' activity for the app.
 * It does app-startup stuff which is initially to start the 'real' main activity.
 */
public class StartupActivity
        extends AppCompatActivity {

    /** the 'LastVersion' i.e. the version which was installed before the current one. */
    public static final String PREF_STARTUP_LAST_VERSION = "Startup.LastVersion";
    /** Indicates the upgrade message has been shown. */
    private static boolean sUpgradeMessageShown;
    /** Self reference for use by tasks and during upgrades. */
    private static WeakReference<StartupActivity> sStartupActivity;

    /** stage the startup is at. */
    private int mStartupStage;

    private TextView mProgressMessageView;
    /** The ViewModel. */
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

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        LocaleUtils.applyPreferred(this);
        setTheme(App.getThemeResId());

        super.onCreate(savedInstanceState);

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
        mModel = new ViewModelProvider(this).get(StartupViewModel.class);
        mModel.init(this);

        if (!initStorage()) {
            // we asked for permissions. TBC in onRequestPermissionsResult
            return;
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
            mModel.getTaskProgressMessage()
                  .observe(this, resId -> mProgressMessageView.setText(getString(resId)));

            // when tasks are done, move on to next startup-stage
            mModel.getTaskFinished().observe(this, finished -> {
                if (finished) {
                    startNextStage();
                }
            });

            // any error, notify user and die.
            mModel.getTaskException().observe(this, e -> {
                if (e != null) {
                    App.showNotification(this, getString(R.string.error_unknown),
                                         e.getLocalizedMessage());
                    finish();
                }
            });

            mModel.startTasks(this);

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
                .setTitle(R.string.lbl_upgrade)
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
     * Prompt the user to make a backup.
     * <p>
     * Note the backup is not done here; we just set a flag if requested.
     */
    private void backupRequired() {
        mModel.setBackupRequired(false);
        if (mModel.isDoPeriodicAction()) {
            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_help_outline)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.warning_backup_request)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        d.dismiss();
                        mModel.setBackupRequired(true);
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

        if (mModel.isBackupRequired()) {
            Intent backupIntent = new Intent(this, BackupActivity.class);
            startActivity(backupIntent);
        }

        // We are done here.
        // Remove the weak self-reference and finish.
        sStartupActivity.clear();
        finish();
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
            UserMessage.show(this, msgId);
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
