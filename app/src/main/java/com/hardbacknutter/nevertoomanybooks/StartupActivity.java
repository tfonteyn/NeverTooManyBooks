/*
 * @Copyright 2018-2023 HardBackNutter
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.hardbacknutter.nevertoomanybooks.core.LoggerFactory;
import com.hardbacknutter.nevertoomanybooks.core.storage.CoverStorageException;
import com.hardbacknutter.nevertoomanybooks.covers.CoverVolume;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityStartupBinding;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsFragment;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

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
    private StartupViewModel vm;

    /** View Binding. */
    private ActivityStartupBinding vb;

    private int volumeChangedOptionChosen;

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
        final Context localizedContext = ServiceLocator.getInstance().getAppLocale().apply(base);
        super.attachBaseContext(localizedContext);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Are we going through a hot/warm start ?
        if (((App) getApplication()).isHotStart()) {
            // yes, skip the entire startup process
            startActivity(new Intent(this, BooksOnBookshelf.class));
            finish();
            return;
        }

        vm = new ViewModelProvider(this).get(StartupViewModel.class);
        vm.init(this);

        vb = ActivityStartupBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        // Display the version.
        final PackageInfoWrapper info = PackageInfoWrapper.create(this);
        vb.version.setText(info.getVersionName());

        vm.onProgress().observe(this, message -> onProgress(message.text));

        // when all tasks are done, move on to next startup-stage
        vm.onFinished().observe(this, message -> message.getData().ifPresent(
                data -> nextStage(Stage.RunTasks)));

        // Not called for now, see {@link StartupViewModel} #mTaskListener.
        vm.onFailure().observe(this, message -> message.getData().ifPresent(this::onFailure));

        nextStage(Stage.Init);
    }

    /**
     * Startup stages.
     *
     * @param currentStage the current stage
     */
    private void nextStage(@NonNull final Stage currentStage) {
        //noinspection OverlyBroadCatchBlock,CheckStyle
        try {
            switch (currentStage.next()) {

                case InitStorage: {
                    initStorage();
                    break;
                }
                case InitDb: {
                    // create static self-reference for DBHelper callbacks.
                    sStartupActivity = new WeakReference<>(this);
                    initDb();
                    break;
                }
                case RunTasks: {
                    startTasks();
                    break;
                }
                case Done: {
                    // Remove the static self-reference
                    if (sStartupActivity != null) {
                        sStartupActivity.clear();
                    }
                    // Any future hot start will skip the startup tasks
                    ((App) getApplication()).setHotStart();
                    // and hand over to the real main activity
                    final Intent intent = new Intent(this, BooksOnBookshelf.class);
                    if (vm.isProposeBackup()) {
                        intent.putExtra(BooksOnBookshelfViewModel.BKEY_PROPOSE_BACKUP, true);
                    }
                    startActivity(intent);
                    finish();
                    break;
                }

                case Init:
                    // we'll never get here
                    break;
            }
        } catch (@NonNull final Exception e) {
            // added due to a report of total startup-failure of a new install.
            onFailure(e);
        }
    }

    private void initStorage() {
        final int storedVolumeIndex = CoverVolume.getVolume(this);
        final int actualVolumeIndex;
        try {
            actualVolumeIndex = CoverVolume.initVolume(this, storedVolumeIndex);

        } catch (@NonNull final CoverStorageException e) {
            onFailure(e);
            return;
        }

        if (storedVolumeIndex == actualVolumeIndex) {
            // all ok
            nextStage(Stage.InitStorage);
        } else {
            onStorageVolumeChanged(actualVolumeIndex);
        }
    }

    /**
     * Create/Upgrade/Open the main database as needed.
     */
    private void initDb() {
        // This is crucial, catch ALL exceptions
        //noinspection CheckStyle,OverlyBroadCatchBlock
        try {
            ServiceLocator.getInstance().getDb();
        } catch (@NonNull final Exception e) {
            onFailure(e);
            return;
        }

        nextStage(Stage.InitDb);
    }

    /**
     * Start all essential startup tasks.
     * When the last tasks finishes, it will trigger the next startup stage.
     */
    private void startTasks() {
        if (!vm.startTasks(this)) {
            // If no task were started, simply move to the next startup stage.
            nextStage(Stage.RunTasks);
        }
        // else we wait until all tasks finish and mVm.onFinished() kicks in
    }

    /**
     * Show progress.
     *
     * @param message to display
     */
    public void onProgress(@Nullable final CharSequence message) {
        vb.progressMessage.setText(message);
    }

    /**
     * A fatal error happened preventing startup.
     *
     * @param e as thrown
     */
    private void onFailure(@NonNull final Throwable e) {
        LoggerFactory.getLogger().e(TAG, e);

        String msg = ExMsg
                .map(this, e)
                .orElseGet(() -> getString(R.string.error_unexpected_long,
                                           getString(R.string.pt_maintenance)));

        if (BuildConfig.DEBUG /* always */) {
            msg += "\n" + Arrays.toString(e.getStackTrace());
        }

        final ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText(getString(R.string.app_name), msg);
        clipboard.setPrimaryClip(clip);

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setTitle(R.string.app_name)
                .setMessage(msg)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> finishAndRemoveTask())
                .setOnDismissListener(d -> finishAndRemoveTask())
                .setPositiveButton(R.string.pt_maintenance, (d, w) -> {
                    // We'll TRY to start the maintenance fragment
                    // which gives access to debug options
                    final Intent intent = FragmentHostActivity
                            .createIntent(this, MaintenanceFragment.class);
                    startActivity(intent);
                    finish();
                })
                .create()
                .show();
    }

    private void onStorageVolumeChanged(final int actualVolumeIndex) {
        final StorageManager storage = (StorageManager) getSystemService(
                Context.STORAGE_SERVICE);
        final StorageVolume volume = storage.getStorageVolumes().get(actualVolumeIndex);

        final CharSequence[] items = {
                getString(R.string.option_storage_quit_and_reinsert_sdcard),
                getString(R.string.option_storage_select, volume.getDescription(this)),
                getString(R.string.option_storage_edit_settings)};

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.lbl_storage_settings)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                .setSingleChoiceItems(items, 0, (d, w) -> volumeChangedOptionChosen = w)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    switch (volumeChangedOptionChosen) {
                        case 0: {
                            // exit the app, and let the user insert the correct sdcard
                            finishAndRemoveTask();
                            break;
                        }
                        case 1: {
                            // Just set the new location and continue startup
                            CoverVolume.setVolume(this, actualVolumeIndex);
                            nextStage(Stage.InitStorage);
                            break;
                        }
                        case 2:
                        default: {
                            // take user to the settings screen
                            final Intent intent = FragmentHostActivity
                                    .createIntent(this, SettingsFragment.class)
                                    .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                              Prefs.pk_storage_volume)
                                    .putExtra(SettingsFragment.BKEY_STORAGE_WAS_MISSING, true);

                            startActivity(intent);
                            // and quit, this will make sure the user exists our app afterwards
                            finish();
                            break;
                        }
                    }
                })
                .create()
                .show();
    }

    public enum Stage {
        /** We're starting. */
        Init,
        /** We need storage for the covers. */
        InitStorage,
        /** We need the database; and potentially upgrade it. */
        InitDb,
        /** Optimize, cleanup,... */
        RunTasks,
        /** All done, start the UI. */
        Done;

        /**
         * Get the next stage.
         *
         * @return next stage
         */
        @NonNull
        public Stage next() {
            switch (this) {
                case Init:
                    return InitStorage;

                case InitStorage:
                    return InitDb;

                case InitDb:
                    return RunTasks;

                case RunTasks:
                case Done:
                    break;
            }

            return Done;
        }
    }
}
