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
package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.filechooser.FileChooserBaseActivity;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment;
import com.eleybourn.bookcatalogue.filechooser.FileListerFragmentTask;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.simpletasks.SimpleTaskQueueProgressDialogFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.util.Objects;

/**
 * Lets the user choose an archive file to backup to, or import from.
 *
 * @author pjw
 */
public class BackupAndRestoreActivity
        extends FileChooserBaseActivity
        implements
        ImportDialogFragment.OnImportTypeSelectionDialogResultsListener,
        ExportDialogFragment.OnExportTypeSelectionDialogResultsListener {

    /**
     * ID's to use when kicking of the tasks for doing a backup or restore.
     * We get it back in {@link #onTaskFinished} so we know the type of task.
     * <p>
     * Note: could use {@link #isSave()} of course. But keeping it future proof.
     * Option 3 ? cloud ? etc....
     */
    private static final int TASK_ID_SAVE_TO_ARCHIVE = 1;
    private static final int TASK_ID_READ_FROM_ARCHIVE = 2;

    private final ImportSettings importSettings = new ImportSettings();
    private final ExportSettings exportSettings = new ExportSettings();

    @CallSuper
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);

        setTitle(isSave() ? R.string.lbl_backup : R.string.lbl_import_from_archive);

        Tracker.exitOnCreate(this);
    }

    /**
     * @return the default file name: blank for 'open', date-based for save.
     */
    @NonNull
    private String getDefaultFileName() {
        if (isSave()) {
            final String sqlDate = DateUtils.localSqlDateForToday();
            return BackupFileDetails.ARCHIVE_PREFIX
                    + sqlDate.replace(" ", "-")
                             .replace(":", "")
                    + BackupFileDetails.ARCHIVE_EXTENSION;
        } else {
            return "";
        }
    }

    /**
     * Create the fragment using the last backup for the path,
     * and the default file name (if saving).
     */
    @NonNull
    @Override
    protected FileChooserFragment getChooserFragment() {
        String lastBackupFile = Prefs.getPrefs().getString(BackupManager.PREF_LAST_BACKUP_FILE,
                                                           StorageUtils.getSharedStorage().getAbsolutePath());

        return FileChooserFragment.newInstance(new File(Objects.requireNonNull(lastBackupFile)),
                                               getDefaultFileName());
    }

    /**
     * Get a task suited to building a list of backup files.
     */
    @NonNull
    @Override
    public FileListerFragmentTask getFileLister(@NonNull final File root) {
        return new BackupListerFragmentTask(root);
    }

    /**
     * After a file was selected, ask the user for the next action.
     * - import
     * - cancel
     * - options
     */
    @Override
    public void onOpen(@NonNull final File file) {
        importSettings.file = file;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.import_option_info_all_books)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        // User wants to import all.
                        importSettings.what = ImportSettings.ALL;
                        BackupManager.restore(BackupAndRestoreActivity.this,
                                              TASK_ID_READ_FROM_ARCHIVE, importSettings);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        dialog.dismiss();
                    }
                })
                .setNeutralButton(R.string.btn_options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        // User wants to tune settings first.
                        ImportDialogFragment.newInstance(importSettings)
                                            .show(getSupportFragmentManager(), null);
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * User has set his choices for import... kick of the restore task.
     */
    @Override
    public void onImportTypeSelectionDialogResult(@NonNull final ImportSettings settings) {
        // sanity check
        if (settings.what == ImportSettings.NOTHING) {
            return;
        }
        BackupManager.restore(this, TASK_ID_READ_FROM_ARCHIVE, settings);
    }

    /**
     * If a file was selected, offer the user settings on how to save the archive.
     */
    @Override
    public void onSave(@NonNull final File file) {
        exportSettings.file = file;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.export_info_backup_all)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        // User wants to backup all.
                        exportSettings.what = ExportSettings.ALL;
                        BackupManager.backup(BackupAndRestoreActivity.this,
                                             TASK_ID_SAVE_TO_ARCHIVE,
                                             exportSettings);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        dialog.dismiss();
                    }
                })
                .setNeutralButton(R.string.btn_options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        // User wants to tune settings first.
                        ExportDialogFragment.newInstance(exportSettings)
                                            .show(getSupportFragmentManager(), null);
                    }
                })
                .create();
        dialog.show();

    }

    /**
     * User has set his choices for backup... check them, and kick of the backup task.
     */
    @Override
    public void onExportTypeSelectionDialogResult(@NonNull final ExportSettings settings) {
        // sanity check
        if (settings.what == ExportSettings.NOTHING) {
            return;
        }

        // backup 'since'
        if ((settings.what & ExportSettings.EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (settings.dateFrom == null) {
                String lastBackup = Prefs.getPrefs().getString(BackupManager.PREF_LAST_BACKUP_DATE,
                                                               null);
                if (lastBackup != null && !lastBackup.isEmpty()) {
                    settings.dateFrom = DateUtils.parseDate(lastBackup);
                }
            }
        } else {
            // make sure; cannot have a dateFrom when not asking for a time limited export
            settings.dateFrom = null;
        }

        BackupManager.backup(this, TASK_ID_SAVE_TO_ARCHIVE, settings);
    }

    /** the import/export has finished. */
    @Override
    public void onTaskFinished(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                               final int taskId,
                               final boolean success,
                               final boolean cancelled,
                               @NonNull final FragmentTask task) {

        Object resultSettings = Objects.requireNonNull(task.getTag());

        // Is it a task we care about?
        switch (taskId) {
            case TASK_ID_SAVE_TO_ARCHIVE:
                handleSaveToArchiveResults(success, cancelled, (ExportSettings) resultSettings);
                break;

            case TASK_ID_READ_FROM_ARCHIVE:
                handleReadFromArchiveResults(success, cancelled, (ImportSettings) resultSettings);
                break;
        }
    }

    private void handleReadFromArchiveResults(final boolean success,
                                              final boolean cancelled,
                                              @NonNull final ImportSettings resultSettings) {
        if (!success) {
            String msg = getString(R.string.error_import_failed)
                    + ' ' + getString(R.string.error_storage_not_readable)
                    + "\n\n" + getString(R.string.error_if_the_problem_persists);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_import_from_archive)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog,
                                            final int which) {
                            // Just return; user may want to try again
                            dialog.dismiss();
                        }
                    })
                    .create();
            dialog.show();
            return;
        }
        if (cancelled) {
            // Just return; user may want to try again
            return;
        }

        if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
            Logger.info(this, "Imported: " + resultSettings);
        }
        // see if there are any pre-200 preferences that need migrating.
        if ((resultSettings.what & ImportSettings.PREFERENCES) != 0) {
            Prefs.migratePreV200preferences(
                    BookCatalogueApp.getAppContext()
                                    .getSharedPreferences("bookCatalogue",
                                                          Context.MODE_PRIVATE).getAll()
            );
            // API: 24 -> BookCatalogueApp.getAppContext().deleteSharedPreferences("bookCatalogue");
            BookCatalogueApp.getAppContext()
                            .getSharedPreferences("bookCatalogue", Context.MODE_PRIVATE)
                            .edit().clear().apply();
        }

        // all done
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.progress_end_import_complete)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog,
                                        final int which) {
                        dialog.dismiss();
                        Intent data = new Intent();
                        data.putExtra(UniqueId.BKEY_IMPORT_RESULT_OPTIONS, resultSettings.what);
                        setResult(Activity.RESULT_OK, data);
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    private void handleSaveToArchiveResults(final boolean success,
                                            final boolean cancelled,
                                            @NonNull final ExportSettings resultSettings) {
        if (!success) {
            String msg = getString(R.string.export_error_backup_failed)
                    + ' ' + getString(R.string.error_storage_not_writable)
                    + "\n\n" + getString(R.string.error_if_the_problem_persists);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_backup)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog,
                                            final int which) {
                            // Just return; user may want to try again
                            dialog.dismiss();
                        }
                    })
                    .create();
            dialog.show();
            return;
        }

        if (cancelled) {
            // Just return; user may want to try again
            return;
        }

        // all done
        //noinspection ConstantConditions
        String msg = getString(R.string.export_info_success_archive_details,
                               resultSettings.file.getParent(),
                               resultSettings.file.getName(),
                               Utils.formatFileSize(resultSettings.file.length()));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_backup)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull final DialogInterface dialog,
                                        final int which) {
                        dialog.dismiss();
                        Intent data = new Intent();
                        data.putExtra(UniqueId.BKEY_EXPORT_RESULT_OPTIONS, resultSettings.what);
                        setResult(Activity.RESULT_OK, data);
                        finish();
                    }
                })
                .create();
        dialog.show();
    }

    /**
     * Not needed, there is only ever one task for restore/backup.
     */
    @Override
    public void onAllTasksFinished(@NonNull final SimpleTaskQueueProgressDialogFragment fragment,
                                   final int taskId,
                                   final boolean success,
                                   final boolean cancelled) {
        // Nothing to do here; we really only care when backup tasks finish,
        // and there's only ever one task
    }
}
