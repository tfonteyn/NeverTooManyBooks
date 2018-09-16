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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.BCPreferences;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ImportTypeSelectionDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.MessageDialogFragment;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment;
import com.eleybourn.bookcatalogue.utils.SimpleTaskQueueProgressFragment.FragmentTask;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.util.Date;

/**
 * FileChooser activity to choose an archive file to open/save
 *
 * @author pjw
 */
public class BackupChooser extends FileChooser implements
        MessageDialogFragment.OnMessageDialogResultListener,
        ImportTypeSelectionDialogFragment.OnImportTypeSelectionDialogResultListener,
        ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener {

    // Used when saving state
    private final static String BKEY_FILENAME = "BackupFileSpec";

    // saving or opening
    private static final int TASK_ID_SAVE = 1;
    private static final int TASK_ID_OPEN = 2;

    private static final int DIALOG_OPEN_IMPORT_TYPE = 1;

    private static final String ARCHIVE_EXTENSION = ".bcbk";
    private static final String ARCHIVE_PREFIX = "BookCatalogue-";
    /**
     * The backup file that will be created (if saving)
     */
    private File mBackupFile = null;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the correct title
        this.setTitle(isSaveDialog() ? R.string.backup_to_archive : R.string.import_from_archive);

        if (savedInstanceState != null && savedInstanceState.containsKey(BKEY_FILENAME)) {
            String fileSpec = savedInstanceState.getString(BKEY_FILENAME);
            if (fileSpec == null) {
                throw new RuntimeException("No BKEY_FILENAME passed in ?");
            }
            mBackupFile = new File(fileSpec);
        }
    }

    /**
     * Setup the default file name: blank for 'open', date-based for save
     */
    private String getDefaultFileName() {
        if (isSaveDialog()) {
            final String sqlDate = DateUtils.toLocalSqlDateOnly(new Date());
            return ARCHIVE_PREFIX + sqlDate.replace(" ", "-").replace(":", "") + ARCHIVE_EXTENSION;
        } else {
            return "";
        }
    }

    /**
     * Create the fragment using the last backup for the path, and the default file name (if saving)
     */
    @Override
    protected FileChooserFragment getChooserFragment() {
        return FileChooserFragment.newInstance(BCPreferences.getLastBackupFile(), getDefaultFileName());
    }

    /**
     * Get a task suited to building a list of backup files.
     */
    @Override
    public FileLister getFileLister(@NonNull File root) {
        return new BackupLister(root);
    }

    /**
     * Save the state
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mBackupFile != null) {
            outState.putString(BKEY_FILENAME, mBackupFile.getAbsolutePath());
        }
    }

    /**
     * If a file was selected, restore the archive.
     */
    @Override
    public void onOpen(@NonNull final File file) {
        ImportTypeSelectionDialogFragment frag = ImportTypeSelectionDialogFragment.newInstance(DIALOG_OPEN_IMPORT_TYPE, file);
        frag.show(getSupportFragmentManager(), null);
    }

    /**
     * If a file was selected, save the archive.
     */
    @Override
    public void onSave(@NonNull final File file) {
        ExportTypeSelectionDialogFragment frag = ExportTypeSelectionDialogFragment.newInstance(DIALOG_OPEN_IMPORT_TYPE, file);
        frag.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onTaskFinished(@NonNull final SimpleTaskQueueProgressFragment fragment,
                               final int taskId, final boolean success, final boolean cancelled,
                               @NonNull final FragmentTask task) {

        // Is it a task we care about?
        switch (taskId) {
            case TASK_ID_SAVE: {
                if (!success) {
                    String msg = getString(R.string.backup_failed)
                            + " " + getString(R.string.please_check_sd_writable)
                            + "\n\n" + getString(R.string.if_the_problem_persists);

                    MessageDialogFragment frag = MessageDialogFragment.newInstance(0,
                            R.string.backup_to_archive, msg, android.R.string.ok, 0, 0);
                    frag.show(getSupportFragmentManager(), null);
                    // Just return; user may want to try again
                    return;
                }
                if (cancelled) {
                    // Just return; user may want to try again
                    return;
                }
                // Show a helpful message
                String msg = getString(R.string.archive_complete_details, mBackupFile.getParent(), mBackupFile.getName(), Utils.formatFileSize(mBackupFile.length()));
                MessageDialogFragment frag = MessageDialogFragment.newInstance(TASK_ID_SAVE,
                        R.string.backup_to_archive, msg, android.R.string.ok, 0, 0);
                frag.show(getSupportFragmentManager(), null);
                break;
            }

            case TASK_ID_OPEN: {
                if (!success) {
                    String msg = getString(R.string.import_failed)
                            + " " + getString(R.string.please_check_sd_readable)
                            + "\n\n" + getString(R.string.if_the_problem_persists);

                    MessageDialogFragment frag = MessageDialogFragment.newInstance(0,
                            R.string.import_from_archive, msg, android.R.string.ok, 0, 0);
                    frag.show(getSupportFragmentManager(), null);
                    // Just return; user may want to try again
                    return;
                }
                if (cancelled) {
                    // Just return; user may want to try again
                    return;
                }

                MessageDialogFragment frag = MessageDialogFragment.newInstance(TASK_ID_OPEN,
                        R.string.import_from_archive, R.string.import_complete, android.R.string.ok, 0, 0);
                frag.show(getSupportFragmentManager(), null);
                break;
            }
        }
    }

    @Override
    public void onAllTasksFinished(@NonNull final SimpleTaskQueueProgressFragment fragment,
                                   final int taskId, final boolean success, final boolean cancelled) {
        // Nothing to do here; we really only care when backup tasks finish, and there's only ever one task
    }

    @Override
    public void onMessageDialogResult(final int dialogId, @NonNull final MessageDialogFragment dialog, final int button) {
        switch (dialogId) {
            case 0:
                // Do nothing, our dialogs with ID 0 are only 'FYI' type;
                break;
            case TASK_ID_OPEN:
            case TASK_ID_SAVE:
                finish();
                break;
        }
    }

    @Override
    public void onImportTypeSelectionDialogResult(final int dialogId, @NonNull final DialogFragment dialog,
                                                  @NonNull final ImportTypeSelectionDialogFragment.ImportSettings settings) {
        switch (settings.options) {

            case Importer.IMPORT_ALL:
                BackupManager.restoreCatalogue(this, settings.file, TASK_ID_OPEN, Importer.IMPORT_ALL);
                break;
            case Importer.IMPORT_NEW_OR_UPDATED:
                BackupManager.restoreCatalogue(this, settings.file, TASK_ID_OPEN, Importer.IMPORT_NEW_OR_UPDATED);
                break;
        }
    }

    @Override
    public void onExportTypeSelectionDialogResult(final int dialogId, @NonNull final DialogFragment dialog,
                                                  @NonNull final ExportTypeSelectionDialogFragment.ExportSettings settings) {
        switch (settings.options) {
            case Exporter.EXPORT_ALL:
                mBackupFile = BackupManager.backupCatalogue(this, settings.file, TASK_ID_SAVE, Exporter.EXPORT_ALL, null);
                break;
            case Exporter.EXPORT_NOTHING:
                return;
            default:
                if (settings.dateFrom == null) {
                    String lastBackup = BCPreferences.getLastBackupDate();
                    if (lastBackup != null && !lastBackup.isEmpty()) {
                        settings.dateFrom = DateUtils.parseDate(lastBackup);
                    } else {
                        settings.dateFrom = null;
                    }
                }
                mBackupFile = BackupManager.backupCatalogue(this, settings.file, TASK_ID_SAVE, settings.options, settings.dateFrom);
                break;
        }
    }
}
