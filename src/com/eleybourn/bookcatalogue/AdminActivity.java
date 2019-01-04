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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.csv.CsvExportTask;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.csv.CsvImportTask;
import com.eleybourn.bookcatalogue.backup.ui.BackupAndRestoreActivity;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.database.CoversDBAdapter;
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
import com.eleybourn.bookcatalogue.utils.BundleUtils;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

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
     * This is not in use right now, but leaving it in place.
     *
     * Can be used to automatically trigger an action in {@link #onResume()}.
     */
    private static final String BKEY_DO_AUTO = "do_auto";
    /** supported action: do an export to a CSV file + finishes the activity when done. */
    private static final String BVAL_DO_AUTO_EXPORT = "export";
    /**
     * collected results from all started activities, which we'll pass on up in our own setResult.
     */
    private final Intent resultData = new Intent();
    private boolean mExportToCsvOnStartup = false;
    private boolean mFinishAfterExport = false;

    @NonNull
    private static String[] getExtSdCardPaths(@NonNull final Context context) {
        List<String> paths = new ArrayList<>();
        for (File file : context.getExternalFilesDirs("external")) {
            if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    Logger.info(AdminActivity.class, "Unexpected external file dir: " + file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                }
            }
        }
        return paths.toArray(new String[0]);
    }

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

        Bundle extras = getIntent().getExtras();
        String val = BundleUtils.getStringFromBundles(BKEY_DO_AUTO, extras);
        if (val != null) {
            switch (val) {
                case BVAL_DO_AUTO_EXPORT:
                    mExportToCsvOnStartup = true;
                    mFinishAfterExport = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported BKEY_DO_AUTO option: " + val);
            }
        }

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
        /* DEBUG */
        if (BuildConfig.DEBUG) {
            View v = findViewById(R.id.lbl_debug);
            v.setVisibility(View.VISIBLE);

            v = findViewById(R.id.lbl_debug_tracker_history);
            v.setVisibility(View.VISIBLE);
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Logger.info(this, Tracker.getEventsInfo());
                }
            });

            v = findViewById(R.id.lbl_debug_preferences);
            v.setVisibility(View.VISIBLE);
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.dumpPreferences(null);
                }
            });

            v = findViewById(R.id.lbl_debug_run_code);
            v.setVisibility(View.VISIBLE);
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {

                    System.getProperties().list(System.out);

                    Map<String, String> env = System.getenv();
                    for (String key : env.keySet()) {
                        Logger.info(System.class, "env|key=" + key + ", value=" + env.get(key));
                    }

                    for (File file : AdminActivity.this.getExternalCacheDirs()) {
                        Logger.info(AdminActivity.this, "getExternalCacheDirs|dir=" + file.getAbsolutePath());
                    }
                    for (File file : AdminActivity.this.getExternalFilesDirs(null)) {
                        Logger.info(AdminActivity.this, "getExternalFilesDirs|dir=" + file.getAbsolutePath());
                    }
                    for (File file : AdminActivity.this.getExternalMediaDirs()) {
                        Logger.info(AdminActivity.this, "getExternalMediaDirs|dir=" + file.getAbsolutePath());
                    }
                    Logger.info(AdminActivity.this, "isExternalStorageRemovable=" + Environment.isExternalStorageRemovable());
                    Logger.info(AdminActivity.this, "getDataDirectory=" + Environment.getDataDirectory().getAbsolutePath());
                    Logger.info(AdminActivity.this, "getCacheDir=" + AdminActivity.this.getCacheDir());
                    Logger.info(AdminActivity.this, "getExternalCacheDir=" + AdminActivity.this.getExternalCacheDir());

                    for (String dir : getExtSdCardPaths(AdminActivity.this)) {
                        Logger.info(AdminActivity.this, "getExtSdCardPaths|dir=" + dir);
                    }
                }
            });
        }

        /* Manage Field Visibility */
        {
            View v = findViewById(R.id.lbl_field_visibility);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, SettingsActivity.class);
                    intent.putExtra(UniqueId.FRAGMENT_ID, SettingsActivity.FRAGMENT_FIELD_VISIBILITY);
                    startActivity(intent);
                }
            });
        }

        /* Preferences */
        {
            View v = findViewById(R.id.lbl_preferences);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, SettingsActivity.class);
                    intent.putExtra(UniqueId.FRAGMENT_ID, SettingsActivity.FRAGMENT_GLOBAL_SETTINGS);
                    startActivity(intent);
                }
            });
        }
        /* Export (backup) to Archive */
        {
            View v = findViewById(R.id.lbl_backup);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, BackupAndRestoreActivity.class);
                    intent.putExtra(BackupAndRestoreActivity.BKEY_MODE, BackupAndRestoreActivity.BVAL_MODE_SAVE);
                    startActivityForResult(intent, REQ_ARCHIVE_BACKUP);
                }
            });
        }

        /* Import from Archive - Start the restore activity*/
        {
            View v = findViewById(R.id.lbl_import_from_archive);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, BackupAndRestoreActivity.class);
                    intent.putExtra(BackupAndRestoreActivity.BKEY_MODE, BackupAndRestoreActivity.BVAL_MODE_OPEN);
                    startActivityForResult(intent, REQ_ARCHIVE_RESTORE);
                }
            });
        }

        /* Export to CSV */
        {
            View v = findViewById(R.id.lbl_export);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    exportToCSV();
                }
            });
        }

        /* Import From CSV */
        {
            View v = findViewById(R.id.lbl_import);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Verify - this can be a dangerous operation
                    confirmToImportFromCSV();
                }
            });
        }

        /* Automatically Update Fields from internet*/
        {
            View v = findViewById(R.id.lbl_update_internet);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, UpdateFieldsFromInternetActivity.class);
                    startActivity(intent);
                }
            });
        }

        /* Goodreads Synchronize */
        {
            View v = findViewById(R.id.lbl_sync_with_goodreads);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.importAllFromGoodreads(AdminActivity.this, true);
                }
            });
        }

        /* Goodreads Import */
        {
            View v = findViewById(R.id.lbl_import_all_from_goodreads);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.importAllFromGoodreads(AdminActivity.this, false);
                }
            });
        }

        /* Goodreads Export (send to) */
        {
            View v = findViewById(R.id.lbl_send_books_to_goodreads);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.sendBooksToGoodreads(AdminActivity.this);
                }
            });
        }

        /* Goodreads credentials */
        {
            View v = findViewById(R.id.goodreads_auth);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, GoodreadsRegisterActivity.class);
                    startActivity(intent);
                }
            });
        }

        /* LibraryThing credentials */
        {
            View v = findViewById(R.id.librarything_auth);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, LibraryThingAdminActivity.class);
                    startActivity(intent);
                }
            });
        }

        /* Search sites */
        {
            View v = findViewById(R.id.search_sites);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, SearchAdminActivity.class);
                    startActivityForResult(intent, REQ_ADMIN_SEARCH_SETTINGS);
                }
            });
        }

        /* Start the activity that shows the basic details of GoodReads tasks. */
        {
            View v = findViewById(R.id.lbl_background_tasks);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, TaskQueueListActivity.class);
                    startActivity(intent);
                }
            });
        }

        /* Reset Hints */
        {
            View v = findViewById(R.id.lbl_reset_hints);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    HintManager.resetHints();
                    //Snackbar.make(v, R.string.hints_have_been_reset, Snackbar.LENGTH_LONG).show();
                    StandardDialogs.showUserMessage(AdminActivity.this, R.string.hints_have_been_reset);
                }
            });
        }

        // Erase cover cache
        {
            View v = findViewById(R.id.lbl_erase_cover_cache);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try (CoversDBAdapter coversDBAdapter = CoversDBAdapter.getInstance()) {
                        coversDBAdapter.deleteAll();
                    }
                }
            });
        }

        /* Copy database for tech support */
        {
            View v = findViewById(R.id.lbl_copy_database);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StorageUtils.exportDatabaseFiles(AdminActivity.this);
                    //Snackbar.make(v, R.string.backup_success, Snackbar.LENGTH_LONG).show();
                    StandardDialogs.showUserMessage(AdminActivity.this, R.string.progress_end_backup_success);
                }
            });

        }
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
                public void onClick(final DialogInterface dialog,
                                    final int which) {
                    importFromCSV();
                }
            });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
            new DialogInterface.OnClickListener() {
                public void onClick(final DialogInterface dialog,
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
                SelectOneDialog.selectFileDialog(getLayoutInflater(),
                    getString(R.string.import_warning_csv_file_more_then_one_found),
                    files, new SimpleDialogOnClickListener() {
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
     * Import all data from the passed CSV file spec.
     */
    private void importFromCSV(@NonNull final File file) {
        ImportSettings settings = new ImportSettings(file);
        settings.what = ImportSettings.BOOK_CSV;
        new CsvImportTask(getTaskManager(), settings)
            .start();
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
            case REQ_ARCHIVE_RESTORE: {
                if (resultCode == RESULT_OK) {
                    // no local action needed, pass results up
                    setResult(resultCode, resultData);
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
        Tracker.exitOnActivityResult(this);
    }

    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();
        if (mExportToCsvOnStartup) {
            exportToCSV();
        }
        Tracker.exitOnResume(this);
    }

    /**
     * Called when any background task completes.
     */
    @Override
    public void onTaskFinished(@NonNull final ManagedTask task) {
        super.onTaskFinished(task);

        // If it's an export, handle it
        if (task instanceof CsvExportTask) {
            onExportFinished((CsvExportTask) task);
        }
    }

    private void onExportFinished(@NonNull final CsvExportTask task) {
        if (task.isCancelled()) {
            if (mFinishAfterExport) {
                setResult(Activity.RESULT_OK);
                finish();
            }
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(AdminActivity.this)
            .setTitle(R.string.export_csv_email)
            .setIcon(R.drawable.ic_send)
            .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
            getString(android.R.string.ok),
            new DialogInterface.OnClickListener() {
                public void onClick(@NonNull final DialogInterface dialog,
                                    final int which) {
                    // setup the mail message
                    String subject = '[' + getString(R.string.app_name) + "] " + getString(R.string.lbl_export_to_csv);

                    final Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                    emailIntent.setType("plain/text");
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

                    ArrayList<Uri> uris = new ArrayList<>();
                    try {
                        File csvExportFile = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
                        Uri coverURI = FileProvider.getUriForFile(AdminActivity.this,
                            GenericFileProvider.AUTHORITY, csvExportFile);

                        uris.add(coverURI);
                        emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);


                        startActivity(Intent.createChooser(emailIntent, getString(R.string.send_mail)));
                    } catch (NullPointerException e) {
                        Logger.error(e);
                        StandardDialogs.showUserMessage(AdminActivity.this, R.string.error_email_failed);
                    }

                    dialog.dismiss();
                }
            });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
            getString(android.R.string.cancel),
            new DialogInterface.OnClickListener() {
                public void onClick(@NonNull final DialogInterface dialog,
                                    final int which) {
                    //do nothing
                    dialog.dismiss();
                }
            });

        dialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(@NonNull final DialogInterface dialog) {
                if (mFinishAfterExport) {
                    setResult(Activity.RESULT_OK);
                    finish();
                }
            }
        });

        if (!isFinishing()) {
            try {
                //
                // Catch errors resulting from 'back' being pressed multiple times so that the activity is destroyed
                // before the dialog can be shown.
                // See http://code.google.com/p/android/issues/detail?id=3953
                //
                dialog.show();
            } catch (RuntimeException e) {
                Logger.error(e);
            }
        }
    }
}
