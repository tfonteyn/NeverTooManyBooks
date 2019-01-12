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

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.csv.CsvExportTask;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.csv.CsvImportTask;
import com.eleybourn.bookcatalogue.backup.ui.BackupAndRestoreActivity;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog.SimpleDialogFileItem;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.SelectOneDialog.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsRegisterActivity;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingAdminActivity;
import com.eleybourn.bookcatalogue.settings.SettingsActivity;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.taskqueue.TaskQueueListActivity;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the Administration page.
 *
 * @author Evan Leybourn
 */
public class AdminActivity
        extends BaseActivityWithTasks {

    private static final int REQ_ARCHIVE_BACKUP = 0;
    private static final int REQ_ARCHIVE_RESTORE = 1;

    private static final int REQ_ADMIN_SEARCH_SETTINGS = 10;

    /**
     * collected results from all started activities, which we'll pass on up in our own setResult.
     */
    private final Intent resultData = new Intent();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_functions;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        super.onCreate(savedInstanceState);
        setTitle(R.string.menu_administration_long);
        setupAdminPage();
        Tracker.exitOnCreate(this);
    }

    /**
     * This function builds the Administration page in 4 sections.
     * 1. General management functions
     * 2. Import / Export
     * 3. Credentials
     * 4. Advanced Options
     */
    private void setupAdminPage() {

        /* Manage Field Visibility */
        View v = findViewById(R.id.lbl_field_visibility);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, SettingsActivity.class);
                intent.putExtra(UniqueId.FRAGMENT_ID,
                                SettingsActivity.FRAGMENT_FIELD_VISIBILITY);
                startActivity(intent);
            }
        });


        /* Preferences */
        v = findViewById(R.id.lbl_preferences);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, SettingsActivity.class);
                intent.putExtra(UniqueId.FRAGMENT_ID,
                                SettingsActivity.FRAGMENT_GLOBAL_SETTINGS);
                startActivity(intent);
            }
        });

        /* Export (backup) to Archive */
        v = findViewById(R.id.lbl_backup);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, BackupAndRestoreActivity.class);
                intent.putExtra(BackupAndRestoreActivity.BKEY_MODE,
                                BackupAndRestoreActivity.BVAL_MODE_SAVE);
                startActivityForResult(intent, REQ_ARCHIVE_BACKUP);
            }
        });


        /* Import from Archive - Start the restore activity*/
        v = findViewById(R.id.lbl_import_from_archive);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, BackupAndRestoreActivity.class);
                intent.putExtra(BackupAndRestoreActivity.BKEY_MODE,
                                BackupAndRestoreActivity.BVAL_MODE_OPEN);
                startActivityForResult(intent, REQ_ARCHIVE_RESTORE);
            }
        });


        /* Export to CSV */
        v = findViewById(R.id.lbl_export);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                exportToCSV();
            }
        });


        /* Import From CSV */
        v = findViewById(R.id.lbl_import);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                // Verify - this can be a dangerous operation
                confirmToImportFromCSV();
            }
        });


        /* Automatically Update Fields from internet*/
        v = findViewById(R.id.lbl_update_internet);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           UpdateFieldsFromInternetActivity.class);
                startActivity(intent);
            }
        });


        /* Goodreads Synchronize */
        v = findViewById(R.id.lbl_sync_with_goodreads);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                GoodreadsUtils.importAllFromGoodreads(AdminActivity.this, true);
            }
        });


        /* Goodreads Import */
        v = findViewById(R.id.lbl_import_all_from_goodreads);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                GoodreadsUtils.importAllFromGoodreads(AdminActivity.this, false);
            }
        });


        /* Goodreads Export (send to) */
        v = findViewById(R.id.lbl_send_books_to_goodreads);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                GoodreadsUtils.sendBooksToGoodreads(AdminActivity.this);
            }
        });


        /* Goodreads credentials */
        v = findViewById(R.id.goodreads_auth);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, GoodreadsRegisterActivity.class);
                startActivity(intent);
            }
        });


        /* LibraryThing credentials */
        v = findViewById(R.id.librarything_auth);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, LibraryThingAdminActivity.class);
                startActivity(intent);
            }
        });


        /* Search sites */
        v = findViewById(R.id.search_sites);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, SearchAdminActivity.class);
                startActivityForResult(intent, REQ_ADMIN_SEARCH_SETTINGS);
            }
        });


        /* Start the activity that shows the basic details of GoodReads tasks. */
        v = findViewById(R.id.lbl_background_tasks);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this, TaskQueueListActivity.class);
                startActivity(intent);
            }
        });


        /* Reset Hints */
        v = findViewById(R.id.lbl_reset_hints);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                HintManager.resetHints();
                //Snackbar.make(v, R.string.hints_have_been_reset, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showUserMessage(AdminActivity.this,
                                                R.string.hints_have_been_reset);
            }
        });


        /* Erase cover cache */
        v = findViewById(R.id.lbl_erase_cover_cache);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                    coversDBAdapter.deleteAll();
                }
            }
        });


        /* Copy database for tech support */
        v = findViewById(R.id.lbl_copy_database);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                StorageUtils.exportDatabaseFiles(AdminActivity.this);
                //Snackbar.make(v, R.string.backup_success, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showUserMessage(AdminActivity.this,
                                                R.string.progress_end_backup_success);
            }
        });
    }

    /**
     * Export all data to a CSV file.
     */
    private void exportToCSV() {
        File file = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
        ExportSettings settings = new ExportSettings(file);
        settings.what = ExportSettings.BOOK_CSV;
        new CsvExportTask(getTaskManager(), settings)
                .start();
    }

    /**
     * Ask before importing.
     */
    private void confirmToImportFromCSV() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.warning_import_be_cautious)
                .setTitle(R.string.title_import_book_data)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 importFromCSV();
                             }
                         });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 //do nothing
                             }
                         });
        dialog.show();
    }

    /**
     * Import all data from somewhere on Shared Storage; ask user to disambiguate if necessary.
     */
    private void importFromCSV() {

        List<File> files = StorageUtils.findCsvFiles();
        // If none, exit with message
        if (files.size() == 0) {
            StandardDialogs.showUserMessage(this, R.string.import_error_csv_file_not_found);
        } else {
            if (files.size() == 1) {
                // If only 1, just use it
                importFromCSV(files.get(0));
            } else {
                // If more than one, ask user which file
                // ENHANCE: Consider asking about importing cover images.
                SelectOneDialog.selectFileDialog(
                        getLayoutInflater(),
                        getString(R.string.import_warning_csv_file_more_then_one_found),
                        files,
                        new SimpleDialogOnClickListener() {
                            @Override
                            public void onClick(@NonNull final SimpleDialogItem item) {
                                SimpleDialogFileItem fileItem = (SimpleDialogFileItem) item;
                                importFromCSV(fileItem.getFile());
                            }
                        });
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
        new CsvImportTask(getTaskManager(), settings).start();
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_ADMIN_SEARCH_SETTINGS:
            case REQ_ARCHIVE_BACKUP:
            case REQ_ARCHIVE_RESTORE:
                if (resultCode == RESULT_OK) {
                    // no local action needed, pass results up
                    setResult(resultCode, resultData);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;

        }
        Tracker.exitOnActivityResult(this);
    }

    /**
     * Called when any background task completes.
     *
     * @param task that finished
     */
    @Override
    public void onTaskFinished(@NonNull final ManagedTask task) {
        super.onTaskFinished(task);
        // If it's an export, handle it
        if (task instanceof CsvExportTask) {
            onExportFinished((CsvExportTask) task);
        }
    }

    /**
     * Callback for the CSV export task.
     *
     * @param task that finished
     */
    private void onExportFinished(@NonNull final CsvExportTask task) {
        if (task.isCancelled()) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(AdminActivity.this)
                .setTitle(R.string.export_csv_email)
                .setIcon(R.drawable.ic_send)
                .create();

        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                         getString(android.R.string.ok),
                         new DialogInterface.OnClickListener() {
                             @Override
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 sendMail();
                                 dialog.dismiss();
                             }
                         });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                         getString(android.R.string.cancel),
                         new DialogInterface.OnClickListener() {
                             public void onClick(@NonNull final DialogInterface dialog,
                                                 final int which) {
                                 dialog.dismiss();
                             }
                         });

        if (!isFinishing()) {
            try {
                //
                // Catch errors resulting from 'back' being pressed multiple times so that
                // the activity is destroyed before the dialog can be shown.
                // See http://code.google.com/p/android/issues/detail?id=3953
                //
                dialog.show();
            } catch (RuntimeException e) {
                Logger.error(e);
            }
        }
    }

    public void sendMail() {
        // setup the mail message
        String subject = '[' + getString(R.string.app_name) + "] "
                + getString(R.string.lbl_export_to_csv);

        final Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        ArrayList<Uri> uris = new ArrayList<>();
        try {
            File csvExportFile = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
            Uri coverURI = FileProvider.getUriForFile(AdminActivity.this,
                                                      GenericFileProvider.AUTHORITY,
                                                      csvExportFile);

            uris.add(coverURI);
            emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);


            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_mail)));
        } catch (NullPointerException e) {
            Logger.error(e);
            StandardDialogs.showUserMessage(AdminActivity.this, R.string.error_email_failed);
        }
    }
}
