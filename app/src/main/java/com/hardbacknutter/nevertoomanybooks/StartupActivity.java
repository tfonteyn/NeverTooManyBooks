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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;
import com.hardbacknutter.nevertoomanybooks.utils.UpgradeMessageManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Single Activity to be the 'Main' activity for the app.
 * It does app-startup stuff which is initially to start the 'real' main activity.
 */
public class StartupActivity
        extends AppCompatActivity {

    /** Log tag. */
    private static final String TAG = "StartupActivity";

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

    /**
     * Apply the user-preferred Locale before onCreate is called.
     * Start the QueueManager.
     */
    protected void attachBaseContext(@NonNull final Context base) {

        // https://developer.android.com/reference/android/os/StrictMode
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                               .detectAll()
                                               .penaltyLog()
                                               .build());
        }

        super.attachBaseContext(LocaleUtils.applyLocale(base));

        // create self-reference for DBHelper callbacks.
        sStartupActivity = new WeakReference<>(this);

        // create the singleton QueueManager. This (re)starts stored tasks.
        QueueManager.create(base);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        App.applyTheme(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_startup);
        mProgressMessageView = findViewById(R.id.progressMessage);

        // Version Number
        final TextView view = findViewById(R.id.version);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            view.setText(info.versionName);
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            // ignore
        }

        mModel = new ViewModelProvider(this).get(StartupViewModel.class);
        mModel.onFatalMsgId().observe(this, msgId -> {
            Notifier.show(this, Notifier.CHANNEL_ERROR,
                          getString(R.string.error_unknown), getString(msgId));
            finish();
        });

        mModel.init(this);

        nextStage();
    }

    /**
     * Startup stages.
     */
    private void nextStage() {
        // onCreate being stage 0
        mStartupStage++;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.STARTUP_TASKS) {
            Log.d(TAG, "startNextStage|stage=" + mStartupStage);
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
                throw new UnexpectedValueException(mStartupStage);
        }
    }

    private void startTasks() {
        if (mModel.isStartTasks()) {
            mModel.onTaskProgress().observe(this, message ->
                    mProgressMessageView.setText(message));

            // when all tasks are done, move on to next startup-stage
            mModel.onAllTasksFinished().observe(this, finished -> {
                if (finished) {
                    nextStage();
                }
            });

            // any error, notify user and die.
            mModel.onTaskException().observe(this, e -> {
                if (e != null) {
                    String eMsg = e.getLocalizedMessage();
                    if (eMsg == null) {
                        eMsg = "";
                    }
                    Notifier.show(this, Notifier.CHANNEL_ERROR,
                                  getString(R.string.error_unknown), eMsg);
                    finish();
                }
            });

            mModel.startTasks(this);

        } else {
            nextStage();
        }
    }

    /**
     * Called in UI thread after last startup task completes, or if there are no tasks to queue.
     */
    private void checkForUpgrades() {
        // Display upgrade message if necessary, otherwise go on to next stage
        final String upgradeMessage = mModel.getUpgradeMessage(this);
        if (upgradeMessage != null) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_info)
                    .setTitle(R.string.lbl_about_upgrade)
                    .setMessage(Html.fromHtml(upgradeMessage))
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        UpgradeMessageManager.setUpgradeAcknowledged(this);
                        nextStage();
                    })
                    .create()
                    .show();
        } else {
            nextStage();
        }
    }

    /**
     * Prompt the user to make a backup.
     * <p>
     * Note the backup is not done here; we just set a flag if requested.
     */
    private void backupRequired() {
        if (mModel.isProposeBackup()) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.warning_backup_request)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> mModel.setBackupRequired())
                    .setOnDismissListener(d -> nextStage())
                    .create()
                    .show();
        } else {
            nextStage();
        }
    }

    /**
     * Last step: start the main user activity.
     * If requested earlier, run a backup now.
     */
    private void gotoMainScreen() {
        final Intent main = new Intent(this, BooksOnBookshelf.class);

        if (mModel.isBackupRequired()) {
            main.putExtra(BooksOnBookshelf.START_BACKUP, true);
        }
        startActivity(main);

//        if (mModel.isBackupRequired()) {
//            final Intent backupIntent = new Intent(this, AdminActivity.class)
//                    .putExtra(UniqueId.BKEY_FRAGMENT_TAG, ExportFragment.TAG);
//            startActivity(backupIntent);
//        }

        // We are done here. Remove the weak self-reference and finish.
        sStartupActivity.clear();
        finish();
    }

    /**
     * Used by the DB upgrade procedure.
     *
     * @param messageId to display
     */
    public void onProgress(@StringRes final int messageId) {
        mProgressMessageView.setText(messageId);
    }
}
