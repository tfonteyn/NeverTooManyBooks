/*
 * @Copyright 2020 HardBackNutter
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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.csv.ExportCSVTask;
import com.hardbacknutter.nevertoomanybooks.backup.csv.ImportCSVTask;
import com.hardbacknutter.nevertoomanybooks.backup.ui.ExportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ui.ImportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ui.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.FormattedMessageException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ExportHelperModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ImportHelperModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.TaskModel;

public class ImportExportFragment
        extends Fragment {

    public static final String TAG = "ImportExportFragment";
    static final String BKEY_AUTO_START_BACKUP = TAG + ":autoStartBackup";

    /** standard export file. */
    private static final String CSV_EXPORT_FILE_NAME = "export.csv";

    private static final int REQ_PICK_FILE_FOR_CSV_EXPORT = 1;
    private static final int REQ_PICK_FILE_FOR_CSV_IMPORT = 2;
    private static final int REQ_PICK_FILE_FOR_ARCHIVE_BACKUP = 3;
    private static final int REQ_PICK_FILE_FOR_ARCHIVE_IMPORT = 4;

    private static final int REQ_EXPORT_DATABASE = 10;

    private ProgressDialogFragment mProgressDialog;

    /** ViewModel. */
    private TaskModel mTaskModel;
    /** ViewModel. */
    private ResultDataModel mResultDataModel;

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
        return inflater.inflate(R.layout.fragment_import_export, container, false);
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
        final Bundle args = getArguments();
        if (args != null) {
            autoStartBackup = args.getBoolean(BKEY_AUTO_START_BACKUP, false);
        }

        mTaskModel = new ViewModelProvider(this).get(TaskModel.class);

        mImportHelperModel = new ViewModelProvider(this).get(ImportHelperModel.class);
        mImportHelperModel.getTaskProgressMessage()
                          .observe(getViewLifecycleOwner(), this::onTaskProgressMessage);
        mImportHelperModel.getTaskFinishedMessage()
                          .observe(getViewLifecycleOwner(), this::onImportFinished);

        mExportHelperModel = new ViewModelProvider(this).get(ExportHelperModel.class);
        mExportHelperModel.getTaskProgressMessage()
                          .observe(getViewLifecycleOwner(), this::onTaskProgressMessage);
        mExportHelperModel.getTaskFinishedMessage()
                          .observe(getViewLifecycleOwner(), this::onExportFinished);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog != null) {
            // reconnect after a fragment restart
            mProgressDialog.setCancellable(mTaskModel.getTask());
        }

        final View root = getView();

        // Export (backup) to Archive
        //noinspection ConstantConditions
        root.findViewById(R.id.lbl_backup)
            .setOnClickListener(v -> startBackup());

        // Import from Archive
        root.findViewById(R.id.lbl_import_from_archive)
            .setOnClickListener(v -> startImportFromArchive());

        // Export to CSV
        root.findViewById(R.id.lbl_export)
            .setOnClickListener(v -> startExportToCsv());

        // Import From CSV
        root.findViewById(R.id.lbl_import)
            .setOnClickListener(v -> startImportFromCsv());

        // Export database
        root.findViewById(R.id.lbl_copy_database)
            .setOnClickListener(v -> {
                final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
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
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        // collect all data for passing to the calling Activity
        if (data != null) {
            mResultDataModel.putResultData(data);
        }

        switch (requestCode) {
            case REQ_PICK_FILE_FOR_ARCHIVE_BACKUP: {
                // The user selected a file to backup to.
                // Next step asks for the options and/or starts the Backup task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
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
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
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
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
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
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
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
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
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
                        } catch (@NonNull final IOException e) {
                            Logger.error(getContext(), TAG, e);
                            msgId = R.string.error_backup_failed;
                        }
                        //noinspection ConstantConditions
                        Snackbar.make(getView(), msgId, Snackbar.LENGTH_LONG).show();
                    }
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    private void onTaskProgressMessage(@NonNull final TaskListener.ProgressMessage message) {
        if (mProgressDialog != null) {
            mProgressDialog.onProgress(message);
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Import from Archive */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Step 1 in the archive import procedure: prompt the user for a uri to import.
     */
    private void startImportFromArchive() {
        // or should we use Intent.ACTION_OPEN_DOCUMENT ?
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        startActivityForResult(intent, ImportExportFragment.REQ_PICK_FILE_FOR_ARCHIVE_IMPORT);
    }

    /**
     * Step 2 in the archive import procedure: show the options to the user.
     *
     * @param uri to read from
     */
    private void showImportOptions(@NonNull final Uri uri) {
        ImportHelper importHelper = new ImportHelper(ImportHelper.ALL, uri);
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.import_option_info_all_books)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setNeutralButton(R.string.btn_options, (dialog, which) -> {
                    // ask user what options they want
                    final boolean validDated =
                            BackupManager.archiveHasValidDates(getContext(), uri);
                    ImportHelperDialogFragment.newInstance(importHelper, validDated)
                                              .show(getChildFragmentManager(),
                                                    ImportHelperDialogFragment.TAG);
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        onImportOptionsSet(importHelper))
                .create()
                .show();
    }

    /**
     * Step 3 in the archive import procedure: kick of the task.
     *
     * @param importHelper final options to use
     */
    private void onImportOptionsSet(@NonNull final ImportHelper importHelper) {
        final RestoreTask task =
                new RestoreTask(importHelper, mImportHelperModel.getTaskListener());

        mProgressDialog = ProgressDialogFragment
                .newInstance(R.string.title_importing, false, true, 0);
        mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);

        mTaskModel.setTask(task);
        mProgressDialog.setCancellable(task);
        task.execute();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Import from CSV */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Step 1 in the CSV import procedure: prompt the user for a uri to import.
     */
    private void startImportFromCsv() {
        // Verify - this can be a dangerous operation
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.title_import_book_data)
                .setMessage(R.string.warning_import_be_cautious)
                .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                        dialog.dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // or should we use Intent.ACTION_OPEN_DOCUMENT ?
                    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            // Android bug? When using "text/csv" we cannot select a csv file?
                            .setType("text/*");
                    startActivityForResult(intent, REQ_PICK_FILE_FOR_CSV_IMPORT);
                })
                .create()
                .show();
    }

    /**
     * Step 2 in the CSV import procedure: show the options to the user.
     *
     * @param uri to read
     */
    private void showImportFromCSVOptions(@NonNull final Uri uri) {
        final ImportHelper settings = new ImportHelper(Options.BOOK_CSV, uri);

        final View view = getLayoutInflater().inflate(R.layout.dialog_import_options, null);
        view.findViewById(R.id.cbx_group).setVisibility(View.GONE);

        final Checkable radioNewAndUpdatedBooks = view.findViewById(R.id.radioNewAndUpdatedBooks);
        // propose the careful option.
        radioNewAndUpdatedBooks.setChecked(true);

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_import_from_csv)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> {
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
        //noinspection ConstantConditions
        final ImportCSVTask task = new ImportCSVTask(getContext(), uri, importHelper,
                                                     mImportHelperModel.getTaskListener());

        mProgressDialog = ProgressDialogFragment
                .newInstance(R.string.title_importing, false, true, 0);
        mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);

        mTaskModel.setTask(task);
        mProgressDialog.setCancellable(task);
        task.execute();
    }

    /**
     * Step 4 in the Archive/CSV import procedure.
     *
     * @param message to process
     */
    private void onImportFinished(@NonNull final TaskListener.FinishMessage<ImportHelper> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.taskId) {
            case R.id.TASK_ID_READ_FROM_ARCHIVE:
            case R.id.TASK_ID_CSV_IMPORT: {
                switch (message.status) {
                    case Success: {
                        onImportFinished(R.string.progress_end_import_complete,
                                         message.result);
                        break;
                    }
                    case Failed: {
                        onImportFailed(message.exception);
                        break;
                    }
                    case Cancelled: {
                        onImportFinished(R.string.progress_end_import_partially_complete,
                                         message.result);
                        break;
                    }
                }
                break;
            }
            default: {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "taskId=" + message.taskId);
                }
                break;
            }
        }
    }

    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ImportHelper importHelper) {

        // Check if there are any BookCatalogue preferences that need migrating.
        if ((importHelper.options & Options.PREFERENCES) != 0) {
            //noinspection ConstantConditions
            LegacyPreferences.migrateLegacyPreferences(getContext());
        }

        // Transform the result data into a user friendly report.
        final Importer.Results results = importHelper.getResults();
        final StringBuilder msg = new StringBuilder();

        if (results.booksCreated > 0 || results.booksUpdated > 0) {
            msg.append("\n• ").append(getString(R.string.progress_msg_n_created_m_updated,
                                                getString(R.string.lbl_books),
                                                results.booksCreated, results.booksUpdated));
        }
        if (results.coversCreated > 0 || results.coversUpdated > 0) {
            msg.append("\n• ").append(getString(R.string.progress_msg_n_created_m_updated,
                                                getString(R.string.lbl_covers),
                                                results.coversCreated, results.coversUpdated));
        }
        if ((importHelper.options & Options.BOOK_LIST_STYLES) != 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if ((importHelper.options & Options.PREFERENCES) != 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }

        final int failed = results.failedCsvLines.size();
        if (failed > 0) {
            final int fs;
            final List<Pair<Integer, String>> list;
            // keep it sensible, list maximum 10 lines.
            if (failed > 10) {
                fs = R.string.warning_import_csv_failed_lines_lots;
                list = results.failedCsvLines.subList(0, 9);
            } else {
                fs = R.string.warning_import_csv_failed_lines_some;
                list = results.failedCsvLines;
            }
            msg.append("\n").append(
                    getString(fs, Csv.join("\n", list, true, "• ", element ->
                            getString(R.string.a_bracket_b_bracket,
                                      String.valueOf(element.first), element.second))));
        }

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(titleId)
                .setMessage(msg)
                .setPositiveButton(R.string.done, (dialog, which) -> {
                    mResultDataModel.putResultData(UniqueId.BKEY_IMPORT_RESULT,
                                                   importHelper.options);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK,
                                            mResultDataModel.getResultData());
                    getActivity().finish();
                })
                .create()
                .show();
    }

    private void onImportFailed(@Nullable final Exception e) {
        String msg = null;

        if (e instanceof InvalidArchiveException) {
            msg = getString(R.string.error_import_invalid_archive);

        } else if (e instanceof IOException) {
            //ENHANCE: if (message.exception.getCause() instanceof ErrnoException) {
            //           int errno = ((ErrnoException) message.exception.getCause()).errno;
            msg = getString(R.string.error_storage_not_readable) + "\n\n"
                  + getString(R.string.error_if_the_problem_persists,
                              getString(R.string.lbl_send_debug_info));

        } else if (e instanceof FormattedMessageException) {
            //noinspection ConstantConditions
            msg = ((FormattedMessageException) e).getLocalizedMessage(getContext());
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = getString(R.string.error_unexpected_error);
        }

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.error_import_failed)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Backup */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Step 1 in the backup procedure: prompt the user for a uri to export to.
     */
    private void startBackup() {
        //noinspection ConstantConditions
        final String fileName = BackupManager.getDefaultBackupFileName(getContext());
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQ_PICK_FILE_FOR_ARCHIVE_BACKUP);
    }

    /**
     * Step 2 in the backup procedure: show the options to the user.
     *
     * @param uri to write to
     */
    private void showBackupOptions(@NonNull final Uri uri) {
        final ExportHelper exportHelper = new ExportHelper(ExportHelper.ALL, uri);
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_backup_to_archive)
                .setMessage(R.string.export_info_backup_all)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setNeutralButton(R.string.btn_options, (dialog, which) -> {
                    // ask user what options they want
                    ExportHelperDialogFragment.newInstance(exportHelper)
                                              .show(getChildFragmentManager(),
                                                    ExportHelperDialogFragment.TAG);
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        onBackupOptionsSet(exportHelper))
                .create()
                .show();
    }

    /**
     * Step 3 in the backup procedure: kick of the backup task.
     *
     * @param exportHelper final options to use
     */
    private void onBackupOptionsSet(@NonNull final ExportHelper exportHelper) {
        final BackupTask task = new BackupTask(exportHelper, mExportHelperModel.getTaskListener());

        mProgressDialog = ProgressDialogFragment
                .newInstance(R.string.title_backing_up, false, true, 0);
        mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);

        mTaskModel.setTask(task);
        mProgressDialog.setCancellable(task);
        task.execute();
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Export to CSV */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Step 1 in the CSV export procedure: prompt the user for a uri to export to.
     */
    private void startExportToCsv() {
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, CSV_EXPORT_FILE_NAME);
        startActivityForResult(intent, REQ_PICK_FILE_FOR_CSV_EXPORT);
    }

    /**
     * Step 2 in the CSV export procedure: Start the CSV export task.
     *
     * @param uri to write to
     */
    private void exportToCSV(@NonNull final Uri uri) {
        //noinspection ConstantConditions
        final ExportCSVTask task = new ExportCSVTask(getContext(),
                                                     new ExportHelper(Options.BOOK_CSV, uri),
                                                     mExportHelperModel.getTaskListener());

        mProgressDialog = ProgressDialogFragment
                .newInstance(R.string.title_backing_up, false, true, 0);
        mProgressDialog.show(getChildFragmentManager(), ProgressDialogFragment.TAG);

        mTaskModel.setTask(task);
        mProgressDialog.setCancellable(task);
        task.execute();
    }

    /**
     * Step 4 in the Archive/CSV export procedure.
     *
     * @param message to process
     */
    private void onExportFinished(@NonNull final TaskListener.FinishMessage<ExportHelper> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.taskId) {
            case R.id.TASK_ID_WRITE_TO_ARCHIVE: {
                switch (message.status) {
                    case Success: {
                        if (isResumed()) {
                            onExportFinished(message.result, false);
                        }
                        break;
                    }
                    case Failed: {
                        onExportFailed(message.exception);
                        break;
                    }
                    case Cancelled: {
                        //noinspection ConstantConditions
                        Snackbar.make(getView(), R.string.progress_end_cancelled,
                                      Snackbar.LENGTH_LONG).show();
                        break;
                    }
                }
                break;
            }
            case R.id.TASK_ID_CSV_EXPORT: {
                switch (message.status) {
                    case Success: {
                        if (isResumed()) {
                            onExportFinished(message.result, true);
                        }
                        break;
                    }
                    case Failed: {
                        onExportFailed(message.exception);
                        break;
                    }
                    case Cancelled: {
                        //noinspection ConstantConditions
                        Snackbar.make(getView(), R.string.progress_end_cancelled,
                                      Snackbar.LENGTH_LONG).show();
                        break;
                    }
                }
                break;
            }
            default: {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "taskId=" + message.taskId);
                }
                break;
            }
        }
    }

    private void onExportFinished(@NonNull final ExportHelper exportHelper,
                                  final boolean offerEmail) {
        // Transform the result data into a user friendly report.
        final Exporter.Results results = exportHelper.getResults();
        final StringBuilder msg = new StringBuilder();

        // slightly misleading. The text currently says "processed" but it's really "exported".
        if (results.booksExported > 0) {
            msg.append("\n• ")
               .append(getString(R.string.progress_msg_n_books_processed, results.booksExported));
        }
        if (results.coversExported > 0
            || results.coversMissing[0] > 0
            || results.coversMissing[1] > 0) {
            msg.append("\n• ")
               .append(getString(R.string.progress_msg_n_covers_processed_m_missing,
                                 results.coversExported,
                                 results.coversMissing[0],
                                 results.coversMissing[1]));
        }

        if ((exportHelper.options & Options.BOOK_LIST_STYLES) != 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if ((exportHelper.options & Options.PREFERENCES) != 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }

        if (offerEmail) {
            msg.append("\n\n").append(getString(R.string.confirm_email_export));
        }

        //noinspection ConstantConditions
        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.progress_end_backup_success)
                .setMessage(msg)
                .setPositiveButton(R.string.done, (d, which) -> {
                    mResultDataModel.putResultData(UniqueId.BKEY_EXPORT_RESULT,
                                                   exportHelper.options);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mResultDataModel.getResultData());
                    getActivity().finish();
                })
                .create();

        if (offerEmail) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.btn_email),
                             (d, which) -> emailExportFile(exportHelper));
        }

        dialog.show();
    }

    private void onExportFailed(@Nullable final Exception e) {
        String msg = null;

        if (e instanceof IOException) {
            // see if we can find the exact cause
            if (e.getCause() instanceof ErrnoException) {
                final int errno = ((ErrnoException) e.getCause()).errno;
                // write failed: ENOSPC (No space left on device)
                if (errno == OsConstants.ENOSPC) {
                    msg = getString(R.string.error_storage_no_space_left);
                } else {
                    // write to logfile for future reporting enhancements.
                    //noinspection ConstantConditions
                    Logger.warn(getContext(), TAG, "onExportFailed|errno=" + errno);
                }
            }

            // generic IOException message
            if (msg == null) {
                msg = getString(R.string.error_storage_not_writable) + "\n\n"
                      + getString(R.string.error_if_the_problem_persists,
                                  getString(R.string.lbl_send_debug_info));
            }
        } else if (e instanceof FormattedMessageException) {
            //noinspection ConstantConditions
            msg = ((FormattedMessageException) e).getLocalizedMessage(getContext());
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = getString(R.string.error_unexpected_error);
        }

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.error_backup_failed)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    /**
     * Create and send an email with the export file.
     *
     * @param exportHelper export data
     */
    private void emailExportFile(@NonNull final ExportHelper exportHelper) {

        final String subject = '[' + getString(R.string.app_name) + "] "
                               + getString(R.string.lbl_export_to_csv);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(exportHelper.uri);
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("plain/text")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, getString(R.string.title_send_mail)));
        } catch (@NonNull final NullPointerException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.error_email_failed, Snackbar.LENGTH_LONG).show();
        }
    }
}
