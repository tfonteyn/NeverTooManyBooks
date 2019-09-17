/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertoomanybooks.backup.BackupTask;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Exporter;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Importer;
import com.hardbacknutter.nevertoomanybooks.backup.LegacyPreferences;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.RestoreTask;
import com.hardbacknutter.nevertoomanybooks.backup.csv.ExportCSVTask;
import com.hardbacknutter.nevertoomanybooks.backup.csv.ImportCSVTask;
import com.hardbacknutter.nevertoomanybooks.backup.ui.ExportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ui.ImportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ui.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueListActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTasks;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.ImportTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendBooksTask;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskFinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.FormattedMessageException;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.AdminModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ExportHelperModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.GoodreadsTaskModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ImportHelperModel;

public class AdminFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "AdminFragment";
    static final String BKEY_AUTO_START_BACKUP = TAG + ":autoStartBackup";

    /** standard export file. */
    private static final String CSV_EXPORT_FILE_NAME = "export.csv";

    private static final int REQ_PICK_FILE_FOR_CSV_EXPORT = 1;
    private static final int REQ_PICK_FILE_FOR_CSV_IMPORT = 2;
    private static final int REQ_PICK_FILE_FOR_ARCHIVE_BACKUP = 3;
    private static final int REQ_PICK_FILE_FOR_ARCHIVE_IMPORT = 4;

    private static final int REQ_CSV_EXPORT_EMAILED = 10;
    private static final int REQ_EXPORT_DATABASE = 11;

    private ProgressDialogFragment mProgressDialog;

    /** ViewModel. */
    private AdminModel mModel;

    /** ViewModel for task control. */
    private GoodreadsTaskModel mGoodreadsTaskModel;

    private ExportHelperModel mExportHelperModel;
    private final OptionsDialogBase.OptionsListener<ExportHelper> mExportOptionsListener =
            this::onBackupOptionsSet;

    private ImportHelperModel mImportHelperModel;
    private final OptionsDialogBase.OptionsListener<ImportHelper> mImportOptionsListener =
            this::onImportOptionsSet;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin, container, false);
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        if (ExportHelperDialogFragment.TAG.equals(fragment.getTag())) {
            ((ExportHelperDialogFragment) fragment).setListener(mExportOptionsListener);
        } else if (ImportHelperDialogFragment.TAG.equals(fragment.getTag())) {
            ((ImportHelperDialogFragment) fragment).setListener(mImportOptionsListener);
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        boolean autoStartBackup = false;
        Bundle args = getArguments();
        if (args != null) {
            autoStartBackup = args.getBoolean(BKEY_AUTO_START_BACKUP);
        }

        // Activity scope
        //noinspection ConstantConditions
        mModel = new ViewModelProvider(getActivity()).get(AdminModel.class);

        mGoodreadsTaskModel = new ViewModelProvider(this).get(GoodreadsTaskModel.class);
        mGoodreadsTaskModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mGoodreadsTaskModel.getTaskFinishedMessage().observe(this, this::onGoodreadsTaskFinished);

        mExportHelperModel = new ViewModelProvider(getActivity()).get(ExportHelperModel.class);
        mExportHelperModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mExportHelperModel.getTaskFinishedMessage().observe(this, this::onExportFinished);

        mImportHelperModel = new ViewModelProvider(getActivity()).get(ImportHelperModel.class);
        mImportHelperModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mImportHelperModel.getTaskFinishedMessage().observe(this, this::onImportFinished);

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTask(mModel.getTask());
        }

        View root = getView();

        // Export (backup) to Archive
        //noinspection ConstantConditions
        root.findViewById(R.id.lbl_backup)
            .setOnClickListener(v -> startBackup());

        // Import from Archive
        root.findViewById(R.id.lbl_import_from_archive)
            .setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType("*/*");
                startActivityForResult(intent, REQ_PICK_FILE_FOR_ARCHIVE_IMPORT);
            });

        // Export to CSV
        root.findViewById(R.id.lbl_export)
            .setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .setType("text/csv")
                                        .putExtra(Intent.EXTRA_TITLE, CSV_EXPORT_FILE_NAME);
                startActivityForResult(intent, REQ_PICK_FILE_FOR_CSV_EXPORT);
            });

        // Import From CSV
        root.findViewById(R.id.lbl_import)
            .setOnClickListener(v -> {
                // Verify - this can be a dangerous operation
                //noinspection ConstantConditions
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.title_import_book_data)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.warning_import_be_cautious)
                        .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                            Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                                    // Android bug? When using "text/csv"
                                                    // we cannot select a csv file?
                                                    .setType("text/*");
                            startActivityForResult(intent, REQ_PICK_FILE_FOR_CSV_IMPORT);
                        })
                        .create()
                        .show();
            });


        // Goodreads Import Synchronize
        root.findViewById(R.id.lbl_sync_with_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new ImportTask(getContext(), true, mGoodreadsTaskModel.getTaskListener()).execute();
            });

        // Goodreads Import All
        root.findViewById(R.id.lbl_import_all_from_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new ImportTask(getContext(), false, mGoodreadsTaskModel.getTaskListener())
                        .execute();
            });

        // Goodreads Export Updated
        root.findViewById(R.id.lbl_send_updated_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new SendBooksTask(getContext(), true, mGoodreadsTaskModel.getTaskListener())
                        .execute();
            });

        // Goodreads Export All
        root.findViewById(R.id.lbl_send_all_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new SendBooksTask(getContext(), false, mGoodreadsTaskModel.getTaskListener())
                        .execute();
            });

        /* Start the activity that shows the active GoodReads tasks. */
        root.findViewById(R.id.lbl_background_tasks)
            .setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), TaskQueueListActivity.class);
                startActivity(intent);
            });


        /* Reset Hints */
        root.findViewById(R.id.lbl_reset_tips)
            .setOnClickListener(v -> {
                //noinspection ConstantConditions
                TipManager.reset(getContext());
                UserMessage.show(v, R.string.tip_reset_done);
            });

        /* Erase cover cache */
        root.findViewById(R.id.lbl_erase_cover_cache)
            .setOnClickListener(v -> CoversDAO.deleteAll());

        /* Cleanup files */
        root.findViewById(R.id.lbl_cleanup_files)
            .setOnClickListener(v -> cleanupFiles());

        /* Send debug info */
        root.findViewById(R.id.lbl_send_info)
            .setOnClickListener(v -> sendDebugInfo());

        /* Export database for tech support */
        root.findViewById(R.id.lbl_copy_database)
            .setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQ_EXPORT_DATABASE);
            });

        if (autoStartBackup) {
            startBackup();
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        // collect all data for passing to the calling Activity
        if (data != null) {
            mModel.addToResults(data);
        }

        switch (requestCode) {
            case REQ_PICK_FILE_FOR_ARCHIVE_BACKUP: {
                // The user selected a file to backup to.
                // Next step asks for the options and/or starts the Backup task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    Uri uri = data.getData();
                    if (uri != null) {
                        showBackupOptions(uri);
                    }
                }
                break;
            }
            case REQ_PICK_FILE_FOR_ARCHIVE_IMPORT: {
                // The user selected a file to import from.
                // Next step asks for the options and/or starts the Import task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    Uri uri = data.getData();
                    if (uri != null) {
                        showImportOptions(uri);
                    }
                }
                break;
            }
            case REQ_PICK_FILE_FOR_CSV_EXPORT: {
                // The user selected a file to export to.
                // Next step starts the export task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    Uri uri = data.getData();
                    if (uri != null) {
                        exportToCSV(uri);
                    }
                }
                break;
            }
            case REQ_PICK_FILE_FOR_CSV_IMPORT: {
                // The user selected a file to import.
                // Next step asks for the options and/or starts the import task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    Uri uri = data.getData();
                    if (uri != null) {
                        showImportFromCSVOptions(uri);
                    }
                }
                break;
            }
            case REQ_EXPORT_DATABASE: {
                // The user selected a directory where to export the databases to.
                // Next step is to do the actual export.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    Uri uri = data.getData();
                    if (uri != null) {
                        @StringRes
                        int msgId;
                        //noinspection ConstantConditions
                        DocumentFile dir = DocumentFile.fromTreeUri(getContext(), uri);
                        try {
                            if (dir != null && dir.isDirectory()) {
                                StorageUtils.exportDatabaseFiles(getContext(), dir);
                                msgId = R.string.progress_end_backup_success;
                            } else {
                                msgId = R.string.warning_select_an_existing_folder;
                            }
                        } catch (IOException e) {
                            Logger.error(this, e);
                            msgId = R.string.error_backup_failed;
                        }
                        //noinspection ConstantConditions
                        UserMessage.show(getView(), msgId);
                    }
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

    private void onTaskProgressMessage(@NonNull final TaskProgressMessage message) {
        if (mProgressDialog != null) {
            mProgressDialog.onProgress(message);
        }
    }

    private void onGoodreadsTaskFinished(
            @NonNull final TaskFinishedMessage<Integer> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        //noinspection ConstantConditions
        @NonNull
        View view = getView();

        switch (message.taskId) {
            case R.id.TASK_ID_GR_IMPORT:
            case R.id.TASK_ID_GR_SEND_BOOKS:
            case R.id.TASK_ID_GR_REQUEST_AUTH: {
                //noinspection ConstantConditions
                String msg = GoodreadsTasks.handleResult(getContext(), message);
                if (msg != null) {
                    UserMessage.show(view, msg);
                } else {
                    RequestAuthTask.needsRegistration(getContext(), mGoodreadsTaskModel
                                                                            .getTaskListener());
                }
                break;
            }
            default: {
                Logger.warnWithStackTrace(this, "taskId=" + message.taskId);
                break;
            }
        }
    }

    private String getMessage(@NonNull final Exception e) {
        String msg;
        if (e instanceof FormattedMessageException) {
            //noinspection ConstantConditions
            msg = ((FormattedMessageException) e).getLocalizedMessage(getContext());
        } else {
            msg = e.getLocalizedMessage();
        }
        return msg;
    }

    private void cleanupFiles() {
        //noinspection ConstantConditions
        String msg = getString(R.string.info_cleanup_files_text,
                               StorageUtils.formatFileSize(getContext(),
                                                           StorageUtils.purgeFiles(false)));

        new AlertDialog.Builder(getContext())
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_cleanup_files)
                .setMessage(msg)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok,
                                   (dialog, which) -> StorageUtils.purgeFiles(true))
                .create()
                .show();
    }

    private void sendDebugInfo() {
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.debug)
                .setMessage(R.string.debug_send_info_text)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!DebugReport.sendDebugInfo(getContext())) {
                        //noinspection ConstantConditions
                        UserMessage.show(getView(), R.string.error_email_failed);
                    }
                })
                .create()
                .show();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Import */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Step 2 in the archive import procedure: show the options to the user.
     * onActivityResult took the uri resulting from step 1
     *
     * @param uri to read from
     */
    private void showImportOptions(@NonNull final Uri uri) {
        ImportHelper importHelper = new ImportHelper(ImportHelper.ALL, uri);
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.import_option_info_all_books)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, which) -> {
                    // ask user what options they want
                    FragmentManager fm = getChildFragmentManager();
                    if (fm.findFragmentByTag(ImportHelperDialogFragment.TAG) == null) {
                        boolean validDated =
                                BackupManager.archiveHasValidDates(getContext(), uri);
                        ImportHelperDialogFragment.newInstance(importHelper, validDated)
                                                  .show(fm, ImportHelperDialogFragment.TAG);
                    }
                })
                .setPositiveButton(android.R.string.ok,
                                   (d, which) -> onImportOptionsSet(importHelper))
                .create()
                .show();
    }

    /**
     * Step 3 in the archive import procedure: kick of the task.
     * Either called directly from Step 2; or with a detour after showing the user the options.
     *
     * @param importHelper final options to use
     */
    private void onImportOptionsSet(@NonNull final ImportHelper importHelper) {
        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.title_importing, false, 0);
            mProgressDialog.show(fm, TAG);

            RestoreTask task = new RestoreTask(importHelper,
                                               mImportHelperModel.getTaskListener());
            mModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mModel.getTask());
    }

    /**
     * Step 2 in the CSV import procedure: show the options to the user.
     * onActivityResult took the uri resulting from step 1.
     *
     * @param uri to read
     */
    private void showImportFromCSVOptions(@NonNull final Uri uri) {
        ImportHelper settings = new ImportHelper(Options.BOOK_CSV, uri);

        View content = getLayoutInflater().inflate(R.layout.dialog_import_options, null);
        content.findViewById(R.id.cbx_group).setVisibility(View.GONE);

        Checkable radioNewAndUpdatedBooks = content.findViewById(R.id.radioNewAndUpdatedBooks);
        // propose the careful option.
        radioNewAndUpdatedBooks.setChecked(true);

        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.lbl_import_from_csv)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (radioNewAndUpdatedBooks.isChecked()) {
                        settings.options |= ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED;
                    }
                    importFromCSV(uri, settings);
                })
                .create()
                .show();
    }

    /**
     * Step 3 in the CSV import procedure: kick of the task.
     *
     * @param uri          to read
     * @param importHelper how to import
     */
    private void importFromCSV(@NonNull final Uri uri,
                               final ImportHelper importHelper) {
        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.title_importing, false, 0);
            mProgressDialog.show(fm, TAG);

            //noinspection ConstantConditions
            ImportCSVTask task = new ImportCSVTask(getContext(), uri, importHelper,
                                                   mImportHelperModel.getTaskListener());
            mModel.setTask(task);
            task.execute();

        }
        mProgressDialog.setTask(mModel.getTask());
    }


    private void onImportFinished(@NonNull final TaskFinishedMessage<ImportHelper> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        //noinspection ConstantConditions
        @NonNull
        Context context = getContext();
        //noinspection ConstantConditions
        @NonNull
        View view = getView();

        ImportHelper importHelper = message.result;

        switch (message.taskId) {
            case R.id.TASK_ID_READ_FROM_ARCHIVE: {
                switch (message.status) {
                    case Success: {
                        onImportFinished(R.string.progress_end_import_complete,
                                         importHelper);
                        break;
                    }
                    case Failed: {
                        String msg = getString(R.string.error_storage_not_readable) + "\n\n"
                                     + getString(R.string.error_if_the_problem_persists);

                        new AlertDialog.Builder(context)
                                .setTitle(R.string.error_import_failed)
                                .setMessage(msg)
                                .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                                .create()
                                .show();
                        break;
                    }
                    case Cancelled: {
                        onImportFinished(R.string.progress_end_import_partially_complete,
                                         importHelper);
                        break;
                    }
                }
                break;
            }
            case R.id.TASK_ID_CSV_IMPORT: {
                switch (message.status) {
                    case Success: {
                        onImportFinished(R.string.progress_end_import_complete,
                                         importHelper);
                        break;
                    }
                    case Failed: {
                        if (message.exception != null) {
                            UserMessage.show(view, getMessage(message.exception));
                        } else {
                            UserMessage.show(view, R.string.error_import_failed);
                        }
                        break;
                    }
                    case Cancelled: {
                        // We still might have partially imported some data.
                        onImportFinished(R.string.progress_end_import_partially_complete,
                                         importHelper);
                        break;
                    }
                }
                break;
            }
            default: {
                Logger.warnWithStackTrace(this, "taskId=" + message.taskId);
                break;
            }
        }
    }

    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ImportHelper importHelper) {

        // See if there are any pre-200 preferences that need migrating.
        if ((importHelper.options & Options.PREFERENCES) != 0) {
            //noinspection ConstantConditions
            LegacyPreferences.migratePreV200preferences(getContext());
        }

        String msg = createImportReport(importHelper);
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setTitle(titleId)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    Intent data = new Intent().putExtra(UniqueId.BKEY_IMPORT_RESULT,
                                                        importHelper.options);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, data);
                    getActivity().finish();
                })
                .create()
                .show();
    }

    /**
     * Transform the result data into a user friendly report.
     *
     * @param importHelper import data
     */
    private String createImportReport(@NonNull final ImportHelper importHelper) {
        Importer.Results results = importHelper.getResults();
        StringBuilder msg = new StringBuilder();

        if (results.booksCreated > 0 || results.booksUpdated > 0) {
            msg.append("\n• ")
               .append(getString(R.string.name_colon_value,
                                 getString(R.string.lbl_books),
                                 getString(R.string.progress_msg_n_created_m_updated,
                                           results.booksCreated, results.booksUpdated)));
        }
        if (results.coversCreated > 0 || results.coversUpdated > 0) {
            msg.append("\n• ")
               .append(getString(R.string.name_colon_value,
                                 getString(R.string.lbl_covers),
                                 getString(R.string.progress_msg_n_created_m_updated,
                                           results.coversCreated, results.coversUpdated)));
        }

        if ((importHelper.options & Options.PREFERENCES) != 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }
        if ((importHelper.options & Options.BOOK_LIST_STYLES) != 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if (msg.length() == 0) {
            msg.append("\n• ").append(getString(R.string.done));
        }
        return msg.toString();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Backup/Export */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Step 1 in the backup procedure: ask the user for a filename/location.
     */
    private void startBackup() {
        //noinspection ConstantConditions
        String fileName = BackupManager.getDefaultBackupFileName(getContext());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*")
                                .putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQ_PICK_FILE_FOR_ARCHIVE_BACKUP);
    }

    /**
     * Step 2 in the backup procedure: show the options to the user.
     * onActivityResult took the uri resulting from step 1
     *
     * @param uri to write to
     */
    private void showBackupOptions(@NonNull final Uri uri) {
        ExportHelper exportHelper = new ExportHelper(ExportHelper.ALL, uri);
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.lbl_backup_to_archive)
                .setMessage(R.string.export_info_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, which) -> {
                    // ask user what options they want
                    FragmentManager fm = getChildFragmentManager();
                    if (fm.findFragmentByTag(ExportHelperDialogFragment.TAG) == null) {
                        ExportHelperDialogFragment.newInstance(exportHelper)
                                                  .show(fm, ExportHelperDialogFragment.TAG);
                    }
                })
                .setPositiveButton(android.R.string.ok,
                                   (d, which) -> onBackupOptionsSet(exportHelper))
                .create()
                .show();
    }

    /**
     * Step 3 in the backup procedure: kick of the backup task.
     * Either called directly from Step 2; or with a detour after showing the user the options.
     *
     * @param exportHelper final options to use
     */
    private void onBackupOptionsSet(@NonNull final ExportHelper exportHelper) {
        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment
                                      .newInstance(R.string.title_backing_up, false, 0);
            mProgressDialog.show(fm, TAG);

            BackupTask task = new BackupTask(exportHelper,
                                             mExportHelperModel.getTaskListener());
            mModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mModel.getTask());
    }

    /**
     * Step 2 in the CSV export procedure: Start the CSV export task.
     * onActivityResult took the uri resulting from step 1.
     *
     * @param uri to write to
     */
    private void exportToCSV(@NonNull final Uri uri) {

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.title_backing_up, false, 0);
            mProgressDialog.show(fm, TAG);
            //noinspection ConstantConditions
            ExportCSVTask task = new ExportCSVTask(getContext(),
                                                   new ExportHelper(Options.BOOK_CSV, uri),
                                                   mExportHelperModel.getTaskListener());
            mModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mModel.getTask());
    }


    private void onExportFinished(@NonNull final TaskFinishedMessage<ExportHelper> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        //noinspection ConstantConditions
        @NonNull
        View view = getView();

        ExportHelper exportHelper = message.result;

        switch (message.taskId) {
            case R.id.TASK_ID_WRITE_TO_ARCHIVE: {
                switch (message.status) {
                    case Success: {
                        if (isResumed()) {
                            String msg = createExportReport(exportHelper);

                            //noinspection ConstantConditions
                            new AlertDialog.Builder(getContext())
                                    .setTitle(R.string.progress_end_backup_success)
                                    .setMessage(msg)
                                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                                        Intent data = new Intent()
                                                              .putExtra(UniqueId.BKEY_EXPORT_RESULT,
                                                                        exportHelper.options);
                                        //noinspection ConstantConditions
                                        getActivity().setResult(Activity.RESULT_OK, data);
                                        getActivity().finish();
                                    })
                                    .create()
                                    .show();
                        }
                        break;
                    }
                    case Failed: {
                        String msg = getString(R.string.error_storage_not_writable)
                                     + "\n\n"
                                     + getString(R.string.error_if_the_problem_persists);

                        //noinspection ConstantConditions
                        new AlertDialog.Builder(getContext())
                                .setTitle(R.string.error_backup_failed)
                                .setMessage(msg)
                                .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                                .create()
                                .show();
                        break;
                    }
                    case Cancelled: {
                        UserMessage.show(view, R.string.progress_end_cancelled);
                        break;
                    }
                }
                break;
            }
            case R.id.TASK_ID_CSV_EXPORT: {
                switch (message.status) {
                    case Success: {
                        if (isResumed()) {
                            String msg = createExportReport(exportHelper)
                                         + "\n\n"
                                         + getString(R.string.confirm_email_export);

                            //noinspection ConstantConditions
                            new AlertDialog.Builder(getContext())
                                    .setTitle(R.string.progress_end_backup_success)
                                    .setMessage(msg)
                                    .setNegativeButton(android.R.string.cancel, (d, which) -> {
                                        Intent data = new Intent()
                                                              .putExtra(UniqueId.BKEY_EXPORT_RESULT,
                                                                        exportHelper.options);
                                        //noinspection ConstantConditions
                                        getActivity().setResult(Activity.RESULT_OK, data);
                                        getActivity().finish();
                                    })
                                    .setPositiveButton(android.R.string.ok,
                                                       (d, which) -> emailExportFile(
                                                               exportHelper))
                                    .create()
                                    .show();
                        }
                        break;
                    }
                    case Failed: {
                        if (message.exception != null) {
                            UserMessage.show(view, getMessage(message.exception));
                        } else {
                            UserMessage.show(view, R.string.error_backup_failed);
                        }
                        break;
                    }
                    case Cancelled: {
                        UserMessage.show(getView(), R.string.progress_end_cancelled);
                        break;
                    }
                }
                break;
            }
            default: {
                Logger.warnWithStackTrace(view, "taskId=" + message.taskId);
                break;
            }
        }
    }

    /**
     * Transform the result data into a user friendly report.
     */
    private String createExportReport(@NonNull final ExportHelper exportHelper) {
        Exporter.Results results = exportHelper.getResults();
        StringBuilder msg = new StringBuilder();

        // slightly misleading. The text currently says "processed" but it's really "exported".
        if (results.booksExported > 0) {
            msg.append("\n• ")
               .append(getString(R.string.progress_msg_n_books_processed,
                                 results.booksExported));
        }
        if (results.coversExported > 0 || results.coversMissing > 0) {
            msg.append("\n• ")
               .append(getString(R.string.progress_msg_n_covers_processed_m_missing,
                                 results.coversExported, results.coversMissing));
        }


        if ((exportHelper.options & Options.BOOK_LIST_STYLES) != 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if ((exportHelper.options & Options.PREFERENCES) != 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }
        if (msg.length() == 0) {
            msg.append("\n• ").append(getString(R.string.done));
        }
        return msg.toString();
    }

    /**
     * Create and send an email with the export file.
     *
     * @param exportHelper export data
     */
    private void emailExportFile(@NonNull final ExportHelper exportHelper) {

        String subject = '[' + getString(R.string.app_name) + "] "
                         + getString(R.string.lbl_export_to_csv);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(exportHelper.uri);
        try {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                                    .setType("plain/text")
                                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, getString(R.string.title_send_mail)));
        } catch (@NonNull final NullPointerException e) {
            Logger.error(this, e);
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_email_failed);
        }
    }
}
