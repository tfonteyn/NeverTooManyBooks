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
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.backup.ExportThread;
import com.eleybourn.bookcatalogue.backup.ImportThread;
import com.eleybourn.bookcatalogue.baseactivity.ActivityWithTasks;
import com.eleybourn.bookcatalogue.booklist.BooklistStylesListActivity;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.CoversDbHelper;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogFileItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogItem;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs.SimpleDialogOnClickListener;
import com.eleybourn.bookcatalogue.filechooser.BackupChooser;
import com.eleybourn.bookcatalogue.searches.SearchAdminHosts;
import com.eleybourn.bookcatalogue.searches.SearchAdminOrder;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsRegister;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.searches.librarything.AdministrationLibraryThing;
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
public class AdministrationFunctions extends ActivityWithTasks {
    private static final int ACTIVITY_BOOKSHELF = 1;
    private static final int ACTIVITY_FIELD_VISIBILITY = 2;

    private static final String BKEY_DO_AUTO = "do_auto";
    private static final String BVAL_EXPORT = "export";

    private CatalogueDBAdapter mDb;
    private boolean finish_after = false;
    private boolean mExportOnStartup = false;

    /**
     * Start the archiving activity
     */
    public static void exportToArchive(Activity a) {
        Intent i = new Intent(a, BackupChooser.class);
        i.putExtra(BackupChooser.BKEY_MODE, BackupChooser.BVAL_MODE_SAVE_AS);
        a.startActivity(i);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_functions;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            this.setTitle(R.string.administration_label);

            mDb = new CatalogueDBAdapter(this);
            mDb.open();

            Bundle extras = getIntent().getExtras();
            if (extras != null && extras.containsKey(BKEY_DO_AUTO)) {
                String val = extras.getString(BKEY_DO_AUTO);
                if (val != null) {
                    switch (val) {
                        case BVAL_EXPORT:
                            finish_after = true;
                            mExportOnStartup = true;
                            break;
                        default:
                            Logger.logError("Unsupported BKEY_DO_AUTO option: " + val);
                            break;
                    }
                }
            }
            setupAdminPage();
        } catch (Exception e) {
            Logger.logError(e);
        }
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
            View v = findViewById(R.id.fields_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    manageFields();
                }
            });
        }

        /* Edit Book list styles */
        {
            View v = findViewById(R.id.edit_styles_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    manageBooklistStyles();
                }
            });
        }

        /* Export (backup) to Archive */
        {
            View v = findViewById(R.id.backup_catalogue_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    exportToArchive(AdministrationFunctions.this);
                }
            });
        }

        /* Import from Archive */
        {
            /* Restore Catalogue Link */
            View v = findViewById(R.id.restore_catalogue_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    importFromArchive();
                }
            });
        }

        /* Export to CSV */
        {
            View v = findViewById(R.id.export_label);
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
            View v = findViewById(R.id.import_label);
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

        /* Automatically Update Fields */
        {
            View v = findViewById(R.id.update_internet_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    updateFieldsFromInternet();
                }
            });
        }

        /* Goodreads Synchronize */
        {
            View v = findViewById(R.id.sync_with_goodreads_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.importAllFromGoodreads(AdministrationFunctions.this, true);
                }
            });
        }

        /* Goodreads Import */
        {
            View v = findViewById(R.id.import_all_from_goodreads_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.importAllFromGoodreads(AdministrationFunctions.this, false);
                }
            });
        }

        /* Goodreads Export (send to) */
        {
            View v = findViewById(R.id.send_books_to_goodreads_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GoodreadsUtils.sendBooksToGoodreads(AdministrationFunctions.this);
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
                    Intent i = new Intent(AdministrationFunctions.this, GoodreadsRegister.class);
                    startActivity(i);
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
                    Intent i = new Intent(AdministrationFunctions.this, AdministrationLibraryThing.class);
                    startActivity(i);
                }
            });
        }

        /* Website urls */
        {
            View v = findViewById(R.id.local_websites_urls);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(AdministrationFunctions.this, SearchAdminHosts.class);
                    startActivity(i);
                }
            });
        }

        /* Search order */
        {
            View v = findViewById(R.id.search_order);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(AdministrationFunctions.this, SearchAdminOrder.class);
                    startActivity(i);
                }
            });
        }

        /* Background Tasks */
        {
            View v = findViewById(R.id.background_tasks_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBackgroundTasks();
                }
            });
        }

        /* Reset Hints */
        {
            View v = findViewById(R.id.reset_hints_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    HintManager.resetHints();
                    Toast.makeText(AdministrationFunctions.this, R.string.hints_have_been_reset, Toast.LENGTH_LONG).show();
                }
            });
        }

        // Erase cover cache
        {
            View v = findViewById(R.id.erase_cover_cache_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try (CoversDbHelper coversDbHelper = CoversDbHelper.getInstance(AdministrationFunctions.this)) {
                        coversDbHelper.eraseCoverCache();
                    }
                }
            });
        }

        /* Copy database for tech support */
        {
            View v = findViewById(R.id.backup_label);
            // Make line flash when clicked.
            v.setBackgroundResource(android.R.drawable.list_selector_background);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDb.backupDbFile();
                    Toast.makeText(AdministrationFunctions.this, R.string.backup_success, Toast.LENGTH_LONG).show();
                }
            });

        }
    }

    private void confirmToImportFromCSV() {
        AlertDialog dialog = new AlertDialog.Builder(AdministrationFunctions.this)
                .setMessage(R.string.import_alert)
                .setTitle(R.string.import_data)
                .setIcon(R.drawable.ic_info_outline)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                AdministrationFunctions.this.getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        importFromCSV();
                    }
                });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                AdministrationFunctions.this.getResources().getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        //do nothing
                    }
                });
        dialog.show();
    }

    /**
     * Load the Manage Field Visibility Activity
     */
    private void manageFields() {
        Intent i = new Intent(this, FieldVisibilityActivity.class);
        startActivityForResult(i, ACTIVITY_FIELD_VISIBILITY);
    }

    /**
     * Load the Edit Book List Styles Activity
     */
    private void manageBooklistStyles() {
        BooklistStylesListActivity.startActivity(AdministrationFunctions.this);
    }

    /**
     * Start the restore activity
     */
    private void importFromArchive() {
        Intent i = new Intent(this, BackupChooser.class);
        i.putExtra(BackupChooser.BKEY_MODE, BackupChooser.BVAL_MODE_OPEN);
        startActivity(i);
    }

    /**
     * Export all data to a CSV file
     */
    private void exportToCSV() {
        new ExportThread(getTaskManager()).start();
    }

    /**
     * Import all data from somewhere on shared storage; ask user to disambiguate if necessary
     */
    private void importFromCSV() {
        // Find all possible files (CSV in bookCatalogue directory)
        List<File> files = StorageUtils.findExportFiles();
        // If none, exit with message
        if (files == null || files.size() == 0) {
            Toast.makeText(this, R.string.no_export_files_found, Toast.LENGTH_LONG).show();
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
    private void importFromCSV(String fileSpec) {
        new ImportThread(getTaskManager(), fileSpec).start();
    }

    /**
     * Update blank Fields from internet
     *
     * There is a current limitation that restricts the search to only books with an ISBN
     */
    private void updateFieldsFromInternet() {
        Intent i = new Intent(this, UpdateFromInternet.class);
        startActivity(i);
    }

    /**
     * Start the activity that shows the basic details of background tasks.
     */
    private void showBackgroundTasks() {
        Intent i = new Intent(this, TaskListActivity.class);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDb.close();
    }

    @Override
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
    public void onTaskEnded(ManagedTask task) {
        // If it's an export, handle it
        if (task instanceof ExportThread) {
            onExportFinished((ExportThread) task);
        }
    }

    private void onExportFinished(ExportThread task) {
        if (task.isCancelled()) {
            if (finish_after)
                finish();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(AdministrationFunctions.this)
                .setTitle(R.string.email_export)
                .setIcon(R.drawable.ic_send)
                .create();
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
                getResources().getString(android.R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        // setup the mail message
                        String subject = "[" + getString(R.string.app_name) + "] " + getString(R.string.export_to_csv);

                        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
                        emailIntent.setType("plain/text");
                        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);

                        ArrayList<Uri> uris = new ArrayList<>();
                        try {
                            uris.add(Uri.fromFile(StorageUtils.getExportFile()));
                            emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
                        } catch (NullPointerException e) {
                            Logger.logError(e);
                            Toast.makeText(AdministrationFunctions.this, R.string.export_failed_sdcard, Toast.LENGTH_LONG).show();
                        }

                        dialog.dismiss();
                    }
                });
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,
                getResources().getString(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
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
                Logger.logError(e);
            }
        }
    }
}
