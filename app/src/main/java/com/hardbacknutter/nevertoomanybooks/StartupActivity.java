/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ExportContract;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityStartupBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.NightMode;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * Single Activity to be the 'Main' activity for the app.
 * Does all preparation needed to start {@link BooksOnBookshelf}.
 */
public class StartupActivity
        extends AppCompatActivity {

    private static final String TAG = "StartupActivity";

    /** Self reference for use by database upgrades. */
    private static WeakReference<StartupActivity> sStartupActivity;

    /** The Activity ViewModel. */
    private StartupViewModel mVm;

    /** View Binding. */
    private ActivityStartupBinding mVb;

    private int mVolumeChangedOptionChosen;

    /** Make a backup; when done, move to the next startup stage. */
    private final ActivityResultLauncher<ArchiveEncoding> mExportLauncher =
            registerForActivityResult(new ExportContract(), success -> nextStage());

    /**
     * Kludge to allow the database open-helper to get a reference to the currently running
     * StartupActivity, so it can send progress messages.
     *
     * @return Reference or {@code null}.
     */
    @Nullable
    public static StartupActivity getActiveActivity() {
        return sStartupActivity != null ? sStartupActivity.get() : null;
    }

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        // apply the user-preferred Locale before onCreate is called.
        super.attachBaseContext(ServiceLocator.getInstance().getAppLocale().apply(base));

        // create self-reference for DBHelper callbacks.
        sStartupActivity = new WeakReference<>(this);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        NightMode.getInstance().apply(this);

        super.onCreate(savedInstanceState);

        // Are we going through a hot/warm start ?
        if (((App) getApplication()).isHotStart()) {
            // yes, skip the entire startup process
            startMainActivity();
            return;
        }

        mVb = ActivityStartupBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());

        // Display the version.
        final PackageInfoWrapper info = PackageInfoWrapper.create(this);
        mVb.version.setText(info.getVersionName());

        mVm = new ViewModelProvider(this).get(StartupViewModel.class);
        mVm.init(this);
        mVm.onProgress().observe(this, message -> onProgress(message.text));
        // when all tasks are done, move on to next startup-stage
        mVm.onFinished().observe(this, aVoid -> nextStage());
        mVm.onFailure().observe(this, this::onFailure);

        nextStage();
    }

    /**
     * Startup stages.
     */
    private void nextStage() {
        switch (mVm.getNextStartupStage()) {
            case 1:
                initStorage();
                return;

            case 2:
                initDb();
                return;

            case 3:
                startTasks();
                return;

            case 4:
                proposeBackup();
                return;

            case 5:
                startMainActivity();
                return;

            default:
                throw new IllegalArgumentException(String.valueOf(mVm.getStartupStage()));
        }
    }

    private void initStorage() {
        final int storedVolumeIndex = AppDir.getVolume(this);
        final int actualVolumeIndex;
        try {
            actualVolumeIndex = AppDir.initVolume(this, storedVolumeIndex);

        } catch (@NonNull final ExternalStorageException e) {
            onExternalStorageException(e);
            return;
        }

        if (storedVolumeIndex == actualVolumeIndex) {
            // all ok
            nextStage();

        } else {
            final StorageManager storage = (StorageManager) getSystemService(
                    Context.STORAGE_SERVICE);
            final StorageVolume volume = storage.getStorageVolumes().get(actualVolumeIndex);

            final CharSequence[] items = new CharSequence[]{
                    getString(R.string.lbl_storage_quit_and_reinsert_sdcard),
                    getString(R.string.lbl_storage_select, volume.getDescription(this)),
                    getString(R.string.lbl_edit_settings)};

            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.lbl_storage_volume)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    .setSingleChoiceItems(items, 0, (d, w) -> mVolumeChangedOptionChosen = w)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        switch (mVolumeChangedOptionChosen) {
                            case 0: {
                                // exit the app, and let the user insert the correct sdcard
                                finishAndRemoveTask();
                                break;
                            }
                            case 1: {
                                // Just set the new location and continue startup
                                AppDir.setVolume(this, actualVolumeIndex);
                                nextStage();
                                break;
                            }
                            case 2: {
                                // take user to the settings screen
                                final Intent intent = new Intent(this, SettingsHostActivity.class)
                                        .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG,
                                                  SettingsFragment.TAG)
                                        .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                                  Prefs.pk_storage_volume)
                                        .putExtra(SettingsFragment.BKEY_STORAGE_WAS_MISSING, true);

                                startActivity(intent);
                                // and quit, this will make sure the user exists our app afterwards
                                finish();
                                break;
                            }
                            default:
                                throw new IllegalStateException();
                        }
                    })
                    .create()
                    .show();
        }
    }

    public void initDb() {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            // Create/Upgrade/Open the main database as needed.
            ServiceLocator.getInstance().initialiseDb(global);

        } catch (@NonNull final Exception e) {
            onGenericException(e);
            return;
        }

        // kick of next startup stage
        nextStage();
    }

    /**
     * Start all essential startup tasks.
     * When the last tasks finishes, it will trigger the next startup stage.
     */
    private void startTasks() {
        if (!mVm.startTasks(this)) {
            // If no task were started, simply move to the next startup stage.
            nextStage();
        }
    }

    /**
     * Prompt the user to make a backup.
     */
    private void proposeBackup() {
        if (mVm.isProposeBackup()) {
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.warning_backup_request)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, w) -> mExportLauncher.launch(null))
                    .setOnDismissListener(d -> nextStage())
                    .create()
                    .show();
        } else {
            nextStage();
        }
    }

    /**
     * Last step in the startup process.
     */
    private void startMainActivity() {
        // Any future hot start will skip the startup tasks
        ((App) getApplication()).setHotStart();
        // Create and start the Goodreads QueueManager (only if not already running).
        QueueManager.start();
        // Remove the weak self-reference
        sStartupActivity.clear();
        // and hand over to the real main activity
        startActivity(new Intent(this, BooksOnBookshelf.class));
        finish();
    }


    /**
     * Show progress.
     *
     * @param message to display
     */
    public void onProgress(@Nullable final CharSequence message) {
        mVb.progressMessage.setText(message);
    }

    // Not called for now, see {@link StartupViewModel} #mTaskListener.
    private void onFailure(@NonNull final FinishedMessage<Exception> message) {
        @Nullable
        final Exception e = message.result;

        if (e instanceof ExternalStorageException) {
            onExternalStorageException((ExternalStorageException) e);
        } else {
            onGenericException(e);
        }
    }

    private void onGenericException(@Nullable final Exception e) {
        if (AppDir.Log.exists()) {
            Logger.error(TAG, e, "");
        }

        CharSequence text = null;
        if (e != null) {
            text = e.getLocalizedMessage();
        }
        if (text == null) {
            text = getString(R.string.error_unknown_long, getString(R.string.lbl_send_debug));
        }

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setTitle(R.string.app_name)
                .setMessage(text)
                .setOnDismissListener(d -> finishAndRemoveTask())
                .setNegativeButton(android.R.string.cancel, (d, w) -> finishAndRemoveTask())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    // We'll TRY to start the maintenance fragment
                    // which gives access to debug options
                    final Intent intent = new Intent(this, FragmentHostActivity.class)
                            .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG,
                                      MaintenanceFragment.TAG);
                    startActivity(intent);
                    finish();
                })
                .create()
                .show();
    }

    private void onExternalStorageException(@NonNull final ExternalStorageException e) {
        if (AppDir.Log.exists()) {
            Logger.error(TAG, e, "");
        }

        final CharSequence msg = getString(R.string.error_storage_not_accessible_s,
                                           e.getAppDir().toString());

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setTitle(R.string.app_name)
                .setMessage(msg)
                .setOnDismissListener(d -> finishAndRemoveTask())
                .setPositiveButton(android.R.string.ok, (d, w) -> finishAndRemoveTask())
                .create()
                .show();
    }
}
