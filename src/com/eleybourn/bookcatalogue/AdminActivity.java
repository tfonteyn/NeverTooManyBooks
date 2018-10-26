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
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;

import com.eleybourn.bookcatalogue.backup.CsvExporter;
import com.eleybourn.bookcatalogue.backup.ExportThread;
import com.eleybourn.bookcatalogue.backup.ImportThread;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.booklist.BooklistStylesActivity;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogFileItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.filechooser.BackupChooserActivity;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsRegisterActivity;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingAdminActivity;
import com.eleybourn.bookcatalogue.tasks.ManagedTask;
import com.eleybourn.bookcatalogue.tasks.TaskListActivity;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the Administration page.
 *
 * @author Evan Leybourn
 */
public class AdminActivity extends BaseActivityWithTasks {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_ADMIN;

    private static final String DO_AUTO = "do_auto";
    private static final String DO_AUTO_EXPORT = "export";

    private boolean finish_after = false;
    private boolean mExportOnStartup = false;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_functions;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle(R.string.lbl_administration);

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.containsKey(DO_AUTO)) {
            String val = extras.getString(DO_AUTO);
            if (val != null) {
                switch (val) {
                    case DO_AUTO_EXPORT:
                        finish_after = true;
                        mExportOnStartup = true;
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported DO_AUTO option: " + val);
                }
            }
        }
        setupAdminPage();
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
        {
            View v = findViewById(R.id.lbl_fields);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, FieldVisibilityActivity.class);
                    startActivityForResult(intent, FieldVisibilityActivity.REQUEST_CODE); /* 2f885b11-27f2-40d7-8c8b-fcb4d95a4151 */
                }
            });
        }

        /* Edit Book list styles */
        {
            View v = findViewById(R.id.lbl_edit_styles);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, BooklistStylesActivity.class);
                    startActivityForResult(intent, BooklistStylesActivity.REQUEST_CODE); /* 13854efe-e8fd-447a-a195-47678c0d87e7 */
                }
            });
        }

        /* Export (backup) to Archive */
        {
            View v = findViewById(R.id.lbl_backup_catalogue);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, BackupChooserActivity.class);
                    intent.putExtra(BackupChooserActivity.BKEY_MODE, BackupChooserActivity.BVAL_MODE_SAVE_AS);
                    startActivity(intent);
                }
            });
        }

        /* Import from Archive - Start the restore activity*/
        {
            View v = findViewById(R.id.lbl_restore_catalogue);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, BackupChooserActivity.class);
                    intent.putExtra(BackupChooserActivity.BKEY_MODE, BackupChooserActivity.BVAL_MODE_OPEN);
                    startActivity(intent);
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
                    Intent intent = new Intent(AdminActivity.this, UpdateFromInternetActivity.class);
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
                    startActivity(intent);
                }
            });
        }

        /* Start the activity that shows the basic details of background tasks. */
        {
            View v = findViewById(R.id.lbl_background_tasks);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AdminActivity.this, TaskListActivity.class);
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
                    StandardDialogs.showBriefMessage(AdminActivity.this, R.string.hints_have_been_reset);
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
                    try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(AdminActivity.this)) {
                        coversDbHelper.eraseCoverCache();
                    }
                }
            });
        }

        /* Copy database for tech support */
        {
            View v = findViewById(R.id.lbl_backup);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    StorageUtils.backupDatabaseFile();
                    //Snackbar.make(v, R.string.backup_success, Snackbar.LENGTH_LONG).show();
                    StandardDialogs.showBriefMessage(AdminActivity.this, R.string.backup_success);
                }
            });

        }
    }

    /**
     * Export all data to a CSV file
     */
    private void exportToCSV() {
        new ExportThread(getTaskManager()).start();
    }

    /**
     * Ask before importing
     */
    private void confirmToImportFromCSV() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(R.string.import_alert)
                .setTitle(R.string.import_data)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        importFromCSV();
                    }
                });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        //do nothing
                    }
                });
        dialog.show();
    }

    /**
     * Import all data from somewhere on shared storage; ask user to disambiguate if necessary
     */
    private void importFromCSV() {
        // Find all possible files (CSV in bookCatalogue directory)
        List<File> files = StorageUtils.findCsvFiles();
        // If none, exit with message
        if (files.size() == 0) {
            StandardDialogs.showBriefMessage(this, R.string.no_export_files_found);
        } else {
            if (files.size() == 1) {
                // If only 1, just use it
                importFromCSV(files.get(0).getAbsolutePath());
            } else {
                // If more than one, ask user which file
                // ENHANCE: Consider asking about importing cover images.
                StandardDialogs.selectFileDialog(getLayoutInflater(),
                        getString(R.string.more_than_one_export_file_blah),
                        files, new SimpleDialogOnClickListener() {
                            @Override
                            public void onClick(@NonNull final SimpleDialogItem item) {
                                SimpleDialogFileItem fileItem = (SimpleDialogFileItem) item;
                                importFromCSV(fileItem.getFile().getAbsolutePath());
                            }
                        });
            }
        }
    }

    /**
     * Import all data from the passed CSV file spec
     */
    private void importFromCSV(@NonNull String fileSpec) {
        new ImportThread(getTaskManager(), fileSpec).start();
    }


    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case FieldVisibilityActivity.REQUEST_CODE: /* 2f885b11-27f2-40d7-8c8b-fcb4d95a4151 */
            case BooklistStylesActivity.REQUEST_CODE: /* 13854efe-e8fd-447a-a195-47678c0d87e7 */
                // pass up
                setResult(resultCode, data); /* 7f46620d-7951-4637-8783-b410730cd460 */
                break;
            default:
                Logger.error("onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
                break;
        }
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mExportOnStartup) {
            exportToCSV();
        }
    }

    /**
     * Called when any background task completes
     */
    @Override
    public void onTaskEnded(@NonNull final ManagedTask task) {
        // If it's an export, handle it
        if (task instanceof ExportThread) {
            onExportFinished((ExportThread) task);
        }
    }

    private void onExportFinished(@NonNull final ExportThread task) {
        if (task.isCancelled()) {
            if (finish_after)
                finish();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(AdminActivity.this)
                .setTitle(R.string.email_export)
                .setIcon(R.drawable.ic_send)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(@NonNull final DialogInterface dialog, final int which) {
                        // setup the mail message
                        String subject = "[" + getString(R.string.app_name) + "] " + getString(R.string.export_to_csv);

                        final Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        emailIntent.setType("plain/text");
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

                        ArrayList<Uri> uris = new ArrayList<>();
                        try {
                            uris.add(Uri.fromFile(StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME)));
                            emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_mail)));
                        } catch (NullPointerException e) {
                            Logger.error(e);
                            StandardDialogs.showBriefMessage(AdminActivity.this, R.string.error_export_failed);
                        }

                        dialog.dismiss();
                    }
                });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(@NonNull final DialogInterface dialog, final int which) {
                        //do nothing
                        dialog.dismiss();
                    }
                });

        dialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (finish_after)
                    finish();
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
            } catch (Exception e) {
                Logger.error(e);
            }
        }
    }
}
