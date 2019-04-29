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
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupTask;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Lets the user choose an archive file to backup to.
 *
 * @author pjw
 */
public class BackupActivity
        extends BRBaseActivity
        implements ExportOptionsDialogFragment.OnOptionsListener {

    private static final String TAG = BackupActivity.class.getSimpleName();

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (null == getSupportFragmentManager().findFragmentById(R.id.browser_fragment)) {

            String defaultFilename = getString(R.string.app_name) + '-'
                    + DateUtils.localSqlDateForToday()
                               .replace(" ", "-")
                               .replace(":", "")
                    + BackupFileDetails.ARCHIVE_EXTENSION;

            createFileBrowser(defaultFilename);
        }

        Button confirm = findViewById(R.id.confirm);
        confirm.setText(R.string.btn_confirm_save);
        confirm.setOnClickListener(v -> doBackup());

        setTitle(R.string.lbl_backup);
    }

    /**
     * Local handler for 'Save'. Perform basic validation, and pass on.
     */
    private void doBackup() {
        FileChooserFragment frag = (FileChooserFragment)
                getSupportFragmentManager().findFragmentById(R.id.browser_fragment);
        if (frag != null) {
            File file = frag.getSelectedFile();
            if (file.exists() && !file.isFile()) {
                //noinspection ConstantConditions
                UserMessage.showUserMessage(frag.getView(),
                                            R.string.warning_select_a_non_directory);
                return;
            }

            final ExportSettings settings = new ExportSettings();
            settings.file = file;

            new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_backup)
                    .setMessage(R.string.export_info_backup_all)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setNeutralButton(R.string.btn_options, (d, which) ->
                            // ask user what options he they want
                            ExportOptionsDialogFragment.show(getSupportFragmentManager(), settings))
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        // User wants to backup all.
                        settings.what = ExportSettings.ALL;
                        BackupTask.start(this, getSupportFragmentManager(), settings);
                    })
                    .create()
                    .show();
        }
    }

    /**
     * User has set his choices for backup... check them, and kick of the backup task.
     */
    @Override
    public void opOptionsResult(@NonNull final ExportSettings settings) {
        // sanity check
        if (settings.what == ExportSettings.NOTHING) {
            return;
        }

        // backup 'since'
        if ((settings.what & ExportSettings.EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (settings.dateFrom == null) {
                String lastBackup = App.getPrefs()
                                       .getString(BackupManager.PREF_LAST_BACKUP_DATE, null);
                if (lastBackup != null && !lastBackup.isEmpty()) {
                    settings.dateFrom = DateUtils.parseDate(lastBackup);
                }
            }
        } else {
            // cannot have a dateFrom when not asking for a time limited export
            settings.dateFrom = null;
        }

        BackupTask.start(this, getSupportFragmentManager(), settings);
    }

    /**
     * Listener for tasks.
     *
     * @param taskId  a task identifier
     * @param success {@code true} for success.
     * @param result  - archive backup : {@link ExportSettings}
     *                - archive restore: {@link ImportSettings}
     *                - file lister: not used
     */
    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               @Nullable final Object result) {

        //noinspection SwitchStatementWithTooFewBranches
        switch (taskId) {
            case R.id.TASK_ID_SAVE_TO_ARCHIVE:
                ExportSettings exportSettings = (ExportSettings) Objects.requireNonNull(result);
                handleResults(success, exportSettings);
                break;

            default:
                Logger.warnWithStackTrace(this, "Unknown taskId=" + taskId);
                break;
        }
    }

    private void handleResults(final boolean success,
                               @NonNull final ExportSettings resultSettings) {
        if (success) {
            //noinspection ConstantConditions
            String msg = getString(R.string.export_info_success_archive_details,
                                   resultSettings.file.getParent(),
                                   resultSettings.file.getName(),
                                   Utils.formatFileSize(this, resultSettings.file.length()));

            new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_backup).setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        d.dismiss();
                        Intent data = new Intent().putExtra(UniqueId.BKEY_EXPORT_RESULT,
                                                            resultSettings.what);
                        setResult(Activity.RESULT_OK, data);
                        finish();
                    })
                    .create()
                    .show();
        } else {
            String msg = getString(R.string.error_backup_failed)
                    + ' ' + getString(R.string.error_storage_not_writable)
                    + "\n\n" + getString(R.string.error_if_the_problem_persists);

            new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_backup).setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                    .create()
                    .show();
        }
    }

}
