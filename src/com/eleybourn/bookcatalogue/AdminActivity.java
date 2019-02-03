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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.csv.CsvTasks;
import com.eleybourn.bookcatalogue.backup.ui.BackupAndRestoreActivity;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog.OnClickListener;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog.SimpleDialogFileItem;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsRegisterActivity;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingAdminActivity;
import com.eleybourn.bookcatalogue.settings.FieldVisibilitySettingsFragment;
import com.eleybourn.bookcatalogue.settings.GlobalSettingsFragment;
import com.eleybourn.bookcatalogue.settings.SettingsActivity;
import com.eleybourn.bookcatalogue.tasks.simpletasks.TaskWithProgressDialogFragment;
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
        extends BaseActivity
        implements TaskWithProgressDialogFragment.OnTaskFinishedListener {

    /** requestCode for making a backup to archive. // taskId for exporting CSV. */
    private static final int REQ_ARCHIVE_BACKUP = 0;
    /** requestCode for doing a restore/import from archive.  // taskId for importing CSV. */
    private static final int REQ_ARCHIVE_RESTORE = 1;

    private static final int REQ_ADMIN_SEARCH_SETTINGS = 10;

    /**
     * collected results from all started activities, which we'll pass on up in our own setResult.
     */
    private final Intent mResultData = new Intent();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_functions;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.menu_administration_long);

        /*
         * This function builds the Administration page in 4 sections.
         * 1. General management functions
         * 2. Import / Export
         * 3. Credentials
         * 4. Advanced Options
         */

        /* Manage Field Visibility */
        View v = findViewById(R.id.lbl_field_visibility);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           SettingsActivity.class);
                intent.putExtra(UniqueId.BKEY_FRAGMENT_TAG, FieldVisibilitySettingsFragment.TAG);
                startActivity(intent);
            }
        });


        /* Preferences */
        v = findViewById(R.id.lbl_preferences);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           SettingsActivity.class);
                intent.putExtra(UniqueId.BKEY_FRAGMENT_TAG, GlobalSettingsFragment.TAG);
                startActivity(intent);
            }
        });


        setupImportExport();


        /* Automatically Update Fields from internet*/
        v = findViewById(R.id.lbl_update_internet);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           UpdateFieldsFromInternetActivity.class);
                startActivity(intent);
            }
        });


        setupGoodreadsImportExport();

        setupCredentials();


        /* Search sites */
        v = findViewById(R.id.search_sites);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           SearchAdminActivity.class);
                startActivityForResult(intent, REQ_ADMIN_SEARCH_SETTINGS);
            }
        });


        /* Start the activity that shows the basic details of GoodReads tasks. */
        v = findViewById(R.id.lbl_background_tasks);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           TaskQueueListActivity.class);
                startActivity(intent);
            }
        });


        /* Reset Hints */
        v = findViewById(R.id.lbl_reset_hints);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
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
        v.setOnClickListener(new View.OnClickListener() {
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
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                StorageUtils.exportDatabaseFiles(AdminActivity.this);
                //Snackbar.make(v, R.string.backup_success, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showUserMessage(AdminActivity.this,
                                                R.string.progress_end_backup_success);
            }
        });
    }

    private void setupImportExport() {
        View v;

        /* Export (backup) to Archive */
        v = findViewById(R.id.lbl_backup);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           BackupAndRestoreActivity.class);
                intent.putExtra(BackupAndRestoreActivity.BKEY_MODE,
                                BackupAndRestoreActivity.BVAL_MODE_SAVE);
                startActivityForResult(intent, REQ_ARCHIVE_BACKUP);
            }
        });


        /* Import from Archive - Start the restore activity*/
        v = findViewById(R.id.lbl_import_from_archive);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           BackupAndRestoreActivity.class);
                intent.putExtra(BackupAndRestoreActivity.BKEY_MODE,
                                BackupAndRestoreActivity.BVAL_MODE_OPEN);
                startActivityForResult(intent, REQ_ARCHIVE_RESTORE);
            }
        });


        /* Export to CSV */
        v = findViewById(R.id.lbl_export);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                exportToCSV();
            }
        });


        /* Import From CSV */
        v = findViewById(R.id.lbl_import);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                // Verify - this can be a dangerous operation
                confirmToImportFromCSV();
            }
        });
    }

    private void setupGoodreadsImportExport() {
        View v;
        /* Goodreads Synchronize */
        v = findViewById(R.id.lbl_sync_with_goodreads);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                GoodreadsUtils.importAllFromGoodreads(AdminActivity.this, true);
            }
        });


        /* Goodreads Import */
        v = findViewById(R.id.lbl_import_all_from_goodreads);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                GoodreadsUtils.importAllFromGoodreads(AdminActivity.this, false);
            }
        });


        /* Goodreads Export (send to) */
        v = findViewById(R.id.lbl_send_books_to_goodreads);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                GoodreadsUtils.sendBooksToGoodreads(AdminActivity.this);
            }
        });
    }

    private void setupCredentials() {
        View v;
        /* Goodreads credentials */
        v = findViewById(R.id.goodreads_auth);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           GoodreadsRegisterActivity.class);
                startActivity(intent);
            }
        });


        /* LibraryThing credentials */
        v = findViewById(R.id.librarything_auth);
        // Make line flash when clicked.
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                Intent intent = new Intent(AdminActivity.this,
                                           LibraryThingAdminActivity.class);
                startActivity(intent);
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
        CsvTasks.exportCSV(this, REQ_ARCHIVE_BACKUP, settings);
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
                SimpleDialog.selectFileDialog(
                        getLayoutInflater(),
                        getString(R.string.import_warning_select_csv_file),
                        files,
                        new OnClickListener() {
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
        CsvTasks.importCSV(this, REQ_ARCHIVE_RESTORE, settings);
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        // collect all data
        if (data != null) {
            mResultData.putExtras(data);
        }

        switch (requestCode) {
            case REQ_ADMIN_SEARCH_SETTINGS:
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
     */
    @Override
    public void onTaskFinished(@NonNull final TaskWithProgressDialogFragment fragment,
                               final int taskId,
                               final boolean success,
                               final boolean cancelled,
                               @NonNull final TaskWithProgressDialogFragment.FragmentTask task) {
        switch (taskId) {
            case REQ_ARCHIVE_BACKUP:
                if (!cancelled) {
                    onExportFinished();
                }
                break;

            case REQ_ARCHIVE_RESTORE:
                break;
        }
    }

    /**
     * Callback for the CSV export task.
     */
    private void onExportFinished() {
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
                                 emailCSVFile();
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

    /**
     * Create and send an email with the CSV export file.
     */
    private void emailCSVFile() {
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
