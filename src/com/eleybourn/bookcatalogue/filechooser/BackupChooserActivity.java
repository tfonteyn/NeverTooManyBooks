/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.filechooser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.ui.ArchiveImportOptionsDialogFragment;
import com.eleybourn.bookcatalogue.backup.ui.ExportSettingsDialogFragment;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.util.Objects;

import static com.eleybourn.bookcatalogue.backup.BackupManager.PREF_LAST_BACKUP_FILE;

/**
 * FileChooserBaseActivity activity to choose an archive file to open/save
 *
 * @author pjw
 */
public class BackupChooserActivity extends FileChooserBaseActivity implements
        ArchiveImportOptionsDialogFragment.OnImportTypeSelectionDialogResultsListener,
        ExportSettingsDialogFragment.OnExportTypeSelectionDialogResultsListener {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_BACKUP_CHOOSER;

    /**
     * ID's to use when kicking of the tasks for doing a backup or restore.
     * We get it back in {@link #onTaskFinished} so we know the type of task.
     *
     * Note: could use {@link #isSave()} of course. But keeping it future proof. Option 3 ? cloud ? etc....
     */
    private static final int TASK_ID_SAVE_TO_ARCHIVE = 1;
    private static final int TASK_ID_READ_FROM_ARCHIVE = 2;

    /** The backup file that will be created (if saving) */
    private File mBackupFile;
    private boolean mSuccess;

    @CallSuper
    @Override
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        setTitle(isSave() ? R.string.backup_to_archive : R.string.import_from_archive);

        if (savedInstanceState != null && savedInstanceState.containsKey(UniqueId.BKEY_FILE_SPEC)) {
            mBackupFile = new File(Objects.requireNonNull(savedInstanceState.getString(UniqueId.BKEY_FILE_SPEC)));
        }
        Tracker.exitOnCreate(this);
    }

    /**
     * Setup the default file name: blank for 'open', date-based for save
     */
    @NonNull
    private String getDefaultFileName() {
        if (isSave()) {
            final String sqlDate = DateUtils.localSqlDateForToday();
            return BackupFileDetails.ARCHIVE_PREFIX +
                    sqlDate.replace(" ", "-").replace(":", "") +
                    BackupFileDetails.ARCHIVE_EXTENSION;
        } else {
            return "";
        }
    }

    /**
     * Create the fragment using the last backup for the path, and the default file name (if saving)
     */
    @NonNull
    @Override
    protected FileChooserFragment getChooserFragment() {
        String lastBackupFile = BookCatalogueApp.getStringPreference(PREF_LAST_BACKUP_FILE, StorageUtils.getSharedStorage().getAbsolutePath());
        //TODO: what happens on very first backup ?
        return FileChooserFragment.newInstance(new File(Objects.requireNonNull(lastBackupFile)), getDefaultFileName());
    }

    /**
     * Get a task suited to building a list of backup files.
     */
    @NonNull
    @Override
    public FileListerFragmentTask getFileLister(@NonNull File root) {
        return new BackupListerFragmentTask(root);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mBackupFile != null) {
            outState.putString(UniqueId.BKEY_FILE_SPEC, mBackupFile.getAbsolutePath());
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * If a file was selected, offer the user options on how to restore the archive.
     */
    @Override
    public void onOpen(final @NonNull File file) {
        ArchiveImportOptionsDialogFragment.newInstance(TASK_ID_READ_FROM_ARCHIVE, file)
                .show(getSupportFragmentManager(), null);
    }

    /**
     * If a file was selected, offer the user options on how to save the archive.
     */
    @Override
    public void onSave(final @NonNull File file) {
        ExportSettingsDialogFragment.newInstance(TASK_ID_SAVE_TO_ARCHIVE, file)
                .show(getSupportFragmentManager(), null);
    }

    @Override
    public void onTaskFinished(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                               final int taskId,
                               final boolean success,
                               final boolean cancelled,
                               final @NonNull FragmentTask task) {

        mSuccess = success;
        // Is it a task we care about?
        switch (taskId) {
            case TASK_ID_SAVE_TO_ARCHIVE: {
                handleSaveToArchiveResults(success, cancelled);
                break;
            }

            case TASK_ID_READ_FROM_ARCHIVE: {
                handleReadFromArchiveResults(success, cancelled);
                break;
            }
        }
    }

    private void handleReadFromArchiveResults(final boolean success, final boolean cancelled) {
        if (!success) {
            String msg = getString(R.string.error_import_failed)
                    + " " + getString(R.string.error_import_failed_check_sd_readable)
                    + "\n\n" + getString(R.string.error_if_the_problem_persists);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.import_from_archive)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    })
                    .create();
            dialog.show();
            // Just return; user may want to try again
            return;
        }
        if (cancelled) {
            // Just return; user may want to try again
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.import_from_archive)
                .setMessage(R.string.progress_end_import_complete)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        setResult(mSuccess ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    private void handleSaveToArchiveResults(final boolean success, final boolean cancelled) {
        if (!success) {
            String msg = getString(R.string.error_backup_failed)
                    + " " + getString(R.string.error_backup_failed_check_sd_writable)
                    + "\n\n" + getString(R.string.error_if_the_problem_persists);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.backup_to_archive)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    })
                    .create();
            dialog.show();
            // Just return; user may want to try again
            return;
        }
        if (cancelled) {
            // Just return; user may want to try again
            return;
        }
        // Show a helpful message
        String msg = getString(R.string.info_archive_complete_details,
                mBackupFile.getParent(),
                mBackupFile.getName(),
                Utils.formatFileSize(mBackupFile.length()));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.backup_to_archive)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        setResult(mSuccess ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    @Override
    public void onAllTasksFinished(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                                   final int taskId,
                                   final boolean success,
                                   final boolean cancelled) {
        // Nothing to do here; we really only care when backup tasks finish, and there's only ever one task
    }

    @Override
    public void onImportTypeSelectionDialogResult(final @IdRes int callerId,
                                                  final @NonNull ImportSettings settings) {
        BackupManager.restore(this, callerId, settings);
    }

    /**
     * User has set his choices for backup... so check them and kick of the backup task
     */
    @Override
    public void onExportTypeSelectionDialogResult(final @NonNull DialogFragment dialog,
                                                  final @IdRes int callerId,
                                                  final @NonNull ExportSettings settings) {
        // sanity check
        if (settings.options == ExportSettings.NOTHING) {
            return;
        }

        // backup 'since'
        if ((settings.options & ExportSettings.EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (settings.dateFrom == null) {
                String lastBackup = BookCatalogueApp.getStringPreference(BackupManager.PREF_LAST_BACKUP_DATE, null);
                if (lastBackup != null && !lastBackup.isEmpty()) {
                    settings.dateFrom = DateUtils.parseDate(lastBackup);
                }
            }
        } else {
            Logger.error("sanity double-check... cannot have a dateFrom when not asking for a time limited export");
            settings.dateFrom = null;
        }

        mBackupFile = BackupManager.backup(this, callerId, settings);
    }
}
