/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityStartupBinding;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.Notifier;
import com.hardbacknutter.nevertoomanybooks.viewmodels.StartupViewModel;

/**
 * Single Activity to be the 'Main' activity for the app.
 * Does all preparation needed to start {@link BooksOnBookshelf}.
 */
public class StartupActivity
        extends AppCompatActivity {

    /** Self reference for use by database upgrades. */
    private static WeakReference<StartupActivity> sStartupActivity;

    /** The Activity ViewModel. */
    private StartupViewModel mModel;
    /** The View binding. */
    private ActivityStartupBinding mVb;

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
    protected void attachBaseContext(@NonNull final Context base) {
        super.attachBaseContext(AppLocale.getInstance().apply(base));

        // create self-reference for DBHelper callbacks.
        sStartupActivity = new WeakReference<>(this);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        NightMode.getInstance().apply(this);

        super.onCreate(savedInstanceState);

        // can't function without access to custom directories
        final String msg = AppDir.init(this);
        if (msg != null) {
            showFatalErrorAndFinish(msg);
            return;
        }

        mVb = ActivityStartupBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());

        // Display the version.
        try {
            final PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            mVb.version.setText(info.versionName);
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            // ignore
        }

        mModel = new ViewModelProvider(this).get(StartupViewModel.class);
        mModel.init(this);
        mModel.onProgressUpdate().observe(this, message ->
                mVb.progressMessage.setText(message.text));
        // when all tasks are done, move on to next startup-stage
        mModel.onFinished().observe(this, aVoid -> nextStage());
        // any error, notify the user and die.
        mModel.onFailure().observe(this, e -> {
            if (e.result != null) {
                showFatalErrorAndFinish(e.result.getLocalizedMessage());
            }
        });

        nextStage();
    }

    /**
     * Startup stages.
     */
    private void nextStage() {

        switch (mModel.getNextStartupStage()) {
            case 1:
                startTasks();
                break;

            case 2:
                backupRequired();
                break;

            case 3:
                gotoMainScreen();
                break;

            default:
                throw new IllegalArgumentException(String.valueOf(mModel.getStartupStage()));
        }
    }

    /**
     * Setup the task observers and start the tasks.
     * When the last tasks finishes, it will trigger the next startup stage.
     * <p>
     * If the tasks are not allowed to start, simply move to the next startup stage.
     */
    private void startTasks() {
        if (mModel.isStartTasks()) {
            mModel.startTasks(this);
        } else {
            nextStage();
        }
    }

    /**
     * Prompt the user to make a backup.
     */
    private void backupRequired() {
        if (mModel.isProposeBackup()) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.warning_backup_request)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        final Intent intent = new Intent(this, AdminActivity.class)
                                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, ExportFragment.TAG);
                        startActivityForResult(intent, RequestCode.NAV_PANEL_EXPORT);
                    })
                    .setOnDismissListener(d -> nextStage())
                    .create()
                    .show();
        } else {
            nextStage();
        }
    }

    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.NAV_PANEL_EXPORT:
                nextStage();
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Last steps:
     * Init the search engines
     * Startup the task queue.
     * Finally, start the main user activity.
     */
    private void gotoMainScreen() {
        // Remove the weak self-reference
        sStartupActivity.clear();

        // Setup the search engines
        SearchEngineRegistry.create(this);

        // Create the Goodreads QueueManager. This (re)starts stored tasks.
        QueueManager.create(this);

        startActivity(new Intent(this, BooksOnBookshelf.class));
        // done here
        finish();
    }

    /**
     * Used by the database upgrade procedure.
     *
     * @param messageId to display
     */
    public void onProgress(@StringRes final int messageId) {
        mVb.progressMessage.setText(messageId);
    }

    /**
     * Use a Notification to tell the user this is a good time to panic.
     * TODO: add email button to the notification, as the user cannot access the About screen
     *
     * @param message to show
     */
    public void showFatalErrorAndFinish(@Nullable final CharSequence message) {
        final PendingIntent pendingIntent = Notifier
                .createPendingIntent(this, StartupActivity.class);
        Notifier.getInstance(this)
                .sendError(this, Notifier.ID_GENERIC, pendingIntent,
                           R.string.error_unknown,
                           message != null ? message : "");
        finish();
    }
}
