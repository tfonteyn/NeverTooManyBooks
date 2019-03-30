/*
 * @copyright 2010 Evan Leybourn
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

package com.eleybourn.bookcatalogue;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.csv.ExportCSVTask;
import com.eleybourn.bookcatalogue.backup.csv.ImportCSVTask;
import com.eleybourn.bookcatalogue.backup.ui.BackupAndRestoreActivity;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.TaskQueueListActivity;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * This is the Administration page.
 *
 * @author Evan Leybourn
 */
public class AdminActivity
        extends BaseActivity
        implements ProgressDialogFragment.OnTaskFinishedListener {

    /** requestCode for making a backup to archive. */
    private static final int REQ_ARCHIVE_BACKUP = 0;
    /** requestCode for doing a restore/import from archive. */
    private static final int REQ_ARCHIVE_RESTORE = 1;

    /**
     * collected results from all started activities, which we'll pass on up in our own setResult.
     */
    private final Intent mResultData = new Intent();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.menu_administration_long);

        View v;

        // Export (backup) to Archive
        v = findViewById(R.id.lbl_backup);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(AdminActivity.this,
                                       BackupAndRestoreActivity.class)
                    .putExtra(BackupAndRestoreActivity.BKEY_MODE,
                              BackupAndRestoreActivity.MODE_SAVE);
            startActivityForResult(intent, REQ_ARCHIVE_BACKUP);
        });


        // Import from Archive - Start the restore activity
        v = findViewById(R.id.lbl_import_from_archive);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(AdminActivity.this,
                                       BackupAndRestoreActivity.class)
                    .putExtra(BackupAndRestoreActivity.BKEY_MODE,
                              BackupAndRestoreActivity.MODE_OPEN);
            startActivityForResult(intent, REQ_ARCHIVE_RESTORE);
        });


        // Export to CSV
        v = findViewById(R.id.lbl_export);
        v.setOnClickListener(v1 -> exportToCSV());


        // Import From CSV
        v = findViewById(R.id.lbl_import);
        v.setOnClickListener(v1 -> {
            // Verify - this can be a dangerous operation
            confirmToImportFromCSV();
        });


        /* Automatically Update Fields from internet*/
        v = findViewById(R.id.lbl_update_internet);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(AdminActivity.this,
                                       UpdateFieldsFromInternetActivity.class);
            startActivity(intent);
        });


        // Goodreads Synchronize
        v = findViewById(R.id.lbl_sync_with_goodreads);
        v.setOnClickListener(v1 -> GoodreadsUtils.importAll(AdminActivity.this, true));


        // Goodreads Import
        v = findViewById(R.id.lbl_import_all_from_goodreads);
        v.setOnClickListener(v1 -> GoodreadsUtils.importAll(AdminActivity.this, false));


        // Goodreads Export (send to)
        v = findViewById(R.id.lbl_send_books_to_goodreads);
        v.setOnClickListener(v1 -> GoodreadsUtils.sendBooks(AdminActivity.this));

        /* Start the activity that shows the basic details of GoodReads tasks. */
        v = findViewById(R.id.lbl_background_tasks);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(AdminActivity.this,
                                       TaskQueueListActivity.class);
            startActivity(intent);
        });


        /* Reset Hints */
        v = findViewById(R.id.lbl_reset_hints);
        v.setOnClickListener(v1 -> {
            HintManager.resetHints();
            UserMessage.showUserMessage(v1, R.string.hints_have_been_reset);
        });


        /* Erase cover cache */
        v = findViewById(R.id.lbl_erase_cover_cache);
        v.setOnClickListener(v1 -> {
            try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                coversDBAdapter.deleteAll();
            }
        });


        /* Copy database for tech support */
        v = findViewById(R.id.lbl_copy_database);
        v.setOnClickListener(v1 -> {
            StorageUtils.exportDatabaseFiles();
            UserMessage.showUserMessage(v1, R.string.progress_end_backup_success);
        });
    }

    /**
     * Export all data to a CSV file.
     */
    private void exportToCSV() {
        File file = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
        ExportSettings settings = new ExportSettings(file);
        settings.what = ExportSettings.BOOK_CSV;

        ExportCSVTask.start(getSupportFragmentManager(), settings);
    }

    /**
     * Ask before importing.
     */
    private void confirmToImportFromCSV() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.warning_import_be_cautious)
                .setTitle(R.string.title_import_book_data)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setNegativeButton(android.R.string.cancel, (d, which) -> {/* do nothing */ })
                .setPositiveButton(android.R.string.ok, (d, which) -> importFromCSV())
                .create();
        dialog.show();
    }

    /**
     * Import all data from somewhere on Shared Storage; ask user to disambiguate if necessary.
     */
    private void importFromCSV() {
        List<File> files = StorageUtils.findCsvFiles();
        // If none, exit with message
        if (files.isEmpty()) {
            UserMessage.showUserMessage(this, R.string.import_error_csv_file_not_found);
        } else {
            if (files.size() == 1) {
                // If only 1, just use it
                importFromCSV(files.get(0));
            } else {
                // If more than one, ask user which file
                // ENHANCE: Consider asking about importing cover images.
                SimpleDialog.selectFileDialog(getLayoutInflater(),
                                              getString(R.string.import_warning_select_csv_file),
                                              files,
                                              item -> importFromCSV(item.getItem()));
            }
        }
    }

    /**
     * Import data.
     *
     * @param file the CSV file to read
     */
    private void importFromCSV(@NonNull final File file) {
        ImportSettings settings = new ImportSettings(file);
        settings.what = ImportSettings.BOOK_CSV;
        ImportCSVTask.start(getSupportFragmentManager(), settings);
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        // collect all data
        if (data != null) {
            mResultData.putExtras(data);
        }

        switch (requestCode) {
            case REQ_ARCHIVE_BACKUP:
            case REQ_ARCHIVE_RESTORE:
                if (resultCode == RESULT_OK) {
                    // no local action needed, pass results up
                    setResult(resultCode, mResultData);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    /**
     * Called when a task finishes.
     *
     * @param taskId  a task identifier
     * @param success <tt>true</tt> for success
     * @param result  not used
     */
    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               @Nullable final Object result) {
        switch (taskId) {
            case R.id.TASK_ID_CSV_EXPORT:
                if (success) {
                    onExportFinished();
                }
                break;

            case R.id.TASK_ID_CSV_IMPORT:
                break;
        }
    }

    /**
     * Callback for the CSV export task.
     */
    private void onExportFinished() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.export_csv_email)
                .setIcon(R.drawable.ic_send)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    emailCSVFile();
                    d.dismiss();
                })
                .create();

        if (!isFinishing()) {
            try {
                // Catch errors resulting from 'back' being pressed multiple times so that
                // the activity is destroyed before the dialog can be shown.
                // See http://code.google.com/p/android/issues/detail?id=3953
                dialog.show();
            } catch (RuntimeException e) {
                Logger.error(e);
            }
        }
    }

    /**
     * Create and send an email with the CSV export file.
     */
    private void emailCSVFile() {
        // setup the mail message
        String subject = '[' + getString(R.string.app_name) + "] "
                + getString(R.string.lbl_export_to_csv);

        final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("plain/text")
                .putExtra(Intent.EXTRA_SUBJECT, subject);

        ArrayList<Uri> uris = new ArrayList<>();
        try {
            File csvExportFile = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
            Uri coverURI = FileProvider.getUriForFile(this,
                                                      GenericFileProvider.AUTHORITY,
                                                      csvExportFile);

            uris.add(coverURI);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, getString(R.string.send_mail)));
        } catch (NullPointerException e) {
            Logger.error(e);
            UserMessage.showUserMessage(this, R.string.error_email_failed);
        }
    }
}
