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
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.SimpleTaskQueueProgressDialogFragment.FragmentTask;
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
        ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultsListener,
        ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultsListener {

    /** saving or opening */
    private static final int IS_ERROR = 0;
    private static final int IS_SAVE = 1;
    private static final int IS_OPEN = 2;

    /** The backup file that will be created (if saving) */
    private File mBackupFile;

    @CallSuper
    @Override
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(isSaveDialog() ? R.string.backup_to_archive : R.string.import_from_archive);

        if (savedInstanceState != null && savedInstanceState.containsKey(UniqueId.BKEY_FILE_SPEC)) {
            mBackupFile = new File(Objects.requireNonNull(savedInstanceState.getString(UniqueId.BKEY_FILE_SPEC)));
        }
    }

    /**
     * Setup the default file name: blank for 'open', date-based for save
     */
    @NonNull
    private String getDefaultFileName() {
        if (isSaveDialog()) {
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
    public FileLister getFileLister(@NonNull File root) {
        return new BackupLister(root);
    }

    /**
     * Save the state
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mBackupFile != null) {
            outState.putString(UniqueId.BKEY_FILE_SPEC, mBackupFile.getAbsolutePath());
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * If a file was selected, restore the archive.
     */
    @Override
    public void onOpen(final @NonNull File file) {
        ImportTypeSelectionDialogFragment.newInstance(IS_OPEN, file)
                .show(getSupportFragmentManager(), null);
    }

    /**
     * If a file was selected, save the archive.
     */
    @Override
    public void onSave(final @NonNull File file) {
        ExportTypeSelectionDialogFragment.newInstance(IS_SAVE, file)
                .show(getSupportFragmentManager(), null);
    }

    private boolean mSuccess;

    @Override
    public void onTaskFinished(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                               final int taskId,
                               final boolean success,
                               final boolean cancelled,
                               final @NonNull FragmentTask task) {

        mSuccess = success;
        // Is it a task we care about?
        switch (taskId) {
            case IS_SAVE: {
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
                break;
            }

            case IS_OPEN: {
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
                break;
            }
        }
    }

    @Override
    public void onAllTasksFinished(final @NonNull SimpleTaskQueueProgressDialogFragment fragment,
                                   final int taskId,
                                   final boolean success,
                                   final boolean cancelled) {
        // Nothing to do here; we really only care when backup tasks finish, and there's only ever one task
    }

    @Override
    public void onImportTypeSelectionDialogResult(final @NonNull DialogFragment dialog,
                                                  final @IdRes int callerId,
                                                  final @NonNull ImportTypeSelectionDialogFragment.ImportSettings settings) {
        switch (settings.options) {
            case Importer.IMPORT_ALL:
                BackupManager.restore(this, settings.file, IS_OPEN, Importer.IMPORT_ALL);
                break;
            case Importer.IMPORT_NEW_OR_UPDATED:
                BackupManager.restore(this, settings.file, IS_OPEN, Importer.IMPORT_NEW_OR_UPDATED);
                break;
        }
    }

    @Override
    public void onExportTypeSelectionDialogResult(final @NonNull DialogFragment dialog,
                                                  final @IdRes int callerId,
                                                  final @NonNull ExportTypeSelectionDialogFragment.ExportSettings settings) {
        switch (settings.options) {
            case Exporter.EXPORT_ALL:
                mBackupFile = BackupManager.backup(this, settings.file, IS_SAVE, Exporter.EXPORT_ALL, null);
                break;

            case Exporter.EXPORT_NOTHING:
                return;

            default:
                if (settings.dateFrom == null) {
                    String lastBackup = BookCatalogueApp.getStringPreference(BackupManager.PREF_LAST_BACKUP_DATE, null);
                    if (lastBackup != null && !lastBackup.isEmpty()) {
                        settings.dateFrom = DateUtils.parseDate(lastBackup);
                    } else {
                        settings.dateFrom = null;
                    }
                }
                mBackupFile = BackupManager.backup(this, settings.file, IS_SAVE, settings.options, settings.dateFrom);
                break;
        }
    }
}
