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
import android.content.Context;
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

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainer;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImportExportBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.FormattedMessageException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ExportTaskModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ImportTaskModel;

/**
 * TODO: consider using true background tasks without progress dialog and have them report
 * being finished/failed via a notification. That would allow the user to continue using
 * the app while the export is running.
 */
public class ImportExportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ImportExportFragment";
    /** Used at startup to initiate a n automatic starting of a backup. */
    static final String BKEY_AUTO_START_BACKUP = TAG + ":autoStartBackup";

    private static final int REQ_PICK_FILE_FOR_IMPORT = 1;

    private static final int REQ_PICK_FILE_FOR_EXPORT_CSV = 2;
    private static final int REQ_PICK_FILE_FOR_EXPORT_ARCHIVE = 3;
    private static final int REQ_PICK_FILE_FOR_EXPORT_DATABASE = 4;

    private ProgressDialogFragment mProgressDialog;

    /** ViewModel. */
    private ResultDataModel mResultDataModel;

    /** Export. */
    private ExportTaskModel mExportModel;
    private final OptionsDialogBase.OptionsListener<ExportManager> mExportOptionsListener =
            this::exportPickUri;

    /** Import. */
    private ImportTaskModel mImportModel;
    private final OptionsDialogBase.OptionsListener<ImportManager> mImportOptionsListener =
            helper -> mImportModel.startArchiveImportTask(helper);

    /** View Binding. */
    private FragmentImportExportBinding mVb;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentImportExportBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment fragment) {
        // we need to hookup the listener for the applicable DIALOG.
        // (tasks uses observers already)
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

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        mImportModel = new ViewModelProvider(this).get(ImportTaskModel.class);
        mImportModel.onTaskProgress()
                    .observe(getViewLifecycleOwner(), this::onTaskProgressMessage);
        mImportModel.onTaskFinished()
                    .observe(getViewLifecycleOwner(), this::onImportFinished);

        mExportModel = new ViewModelProvider(this).get(ExportTaskModel.class);
        mExportModel.onTaskProgress()
                    .observe(getViewLifecycleOwner(), this::onTaskProgressMessage);
        mExportModel.onTaskFinished()
                    .observe(getViewLifecycleOwner(), this::onExportFinished);

        // Import
        mVb.lblImport.setOnClickListener(v -> importPickUri());

        // Export (backup) to Archive
        mVb.lblExportArchive.setOnClickListener(v -> exportShowOptions());

        // Export to CSV; we don't ask for options but always do all books
        mVb.lblExportCsv
                .setOnClickListener(v -> exportCsvPickUri(new ExportManager(Options.BOOKS)));

        // Export database - Mainly meant for debug or external processing.
        mVb.lblCopyDatabase.setOnClickListener(v -> exportDatabasePickUri());

        if (autoStartBackup) {
            exportShowOptions();
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
            case REQ_PICK_FILE_FOR_IMPORT: {
                // The user selected a file to import from. Next step asks for the options.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        importShowOptions(uri);
                    }
                }
                break;
            }
            case REQ_PICK_FILE_FOR_EXPORT_ARCHIVE: {
                // The user selected a file to backup to. Next step starts the export task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        mExportModel.startArchiveExportTask(uri);
                    }
                }
                break;
            }
            case REQ_PICK_FILE_FOR_EXPORT_CSV: {
                // The user selected a file to export to. Next step starts the export task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        mExportModel.startExporterTask(uri);
                    }
                }
                break;
            }
            case REQ_PICK_FILE_FOR_EXPORT_DATABASE: {
                // The user a file to export to. Next step is to do the actual export.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        exportDatabase(uri);
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
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog(message.taskId);
        }
        mProgressDialog.onProgress(message);
    }

    private ProgressDialogFragment getOrCreateProgressDialog(final int taskId) {
        FragmentManager fm = getChildFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        // not found? create it
        if (dialog == null) {
            int titleId;
            switch (taskId) {
                case R.id.TASK_ID_READ_FROM_ARCHIVE:
                case R.id.TASK_ID_IMPORTER:
                    titleId = R.string.title_importing;
                    break;
                case R.id.TASK_ID_WRITE_TO_ARCHIVE:
                case R.id.TASK_ID_EXPORTER:
                    titleId = R.string.title_backing_up;
                    break;
                default:
                    throw new UnexpectedValueException("taskId=" + taskId);
            }

            dialog = ProgressDialogFragment.newInstance(titleId, false, true, 0);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        switch (taskId) {
            case R.id.TASK_ID_READ_FROM_ARCHIVE:
            case R.id.TASK_ID_IMPORTER:
                dialog.setCancellable(mImportModel.getTask());
                break;
            case R.id.TASK_ID_WRITE_TO_ARCHIVE:
            case R.id.TASK_ID_EXPORTER:
                dialog.setCancellable(mExportModel.getTask());
                break;
            default:
                throw new UnexpectedValueException("taskId=" + taskId);
        }
        return dialog;
    }

    /* ------------------------------------------------------------------------------------------ */
    /* Import */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Import from Archive: Step 1: prompt the user for a uri to import.
     */
    private void importPickUri() {
        // This does not allow multiple saved files like "foo.tar (1)", "foo.tar (2)"
//        String[] mimeTypes = {"application/x-tar", "text/csv"};
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
//                .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                .setType("*/*");
        startActivityForResult(intent, ImportExportFragment.REQ_PICK_FILE_FOR_IMPORT);
    }

    /**
     * Import from Archive: Step 2: show the options to the user.
     *
     * @param uri file to read from
     */
    private void importShowOptions(@NonNull final Uri uri) {
        // options will be overridden if the import is a CSV.
        ImportManager helper = new ImportManager(Options.ALL, uri);

        //noinspection ConstantConditions
        final ArchiveContainer container = helper.getContainer(getContext());
        if (!helper.isSupported(container)) {
            Snackbar.make(mVb.lblImport, R.string.error_cannot_import,
                          Snackbar.LENGTH_LONG).show();
            return;
        }

        if (container.equals(ArchiveContainer.CsvBooks)) {
            // use more prudent default options for Csv files.
            helper.setOptions(Options.BOOKS | ImportManager.IMPORT_ONLY_NEW_OR_UPDATED);
            // Verify - this can be a dangerous operation
            new MaterialAlertDialogBuilder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.title_import_book_data)
                    .setMessage(R.string.warning_import_be_cautious)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .setPositiveButton(android.R.string.ok,
                                       (dialog, which) -> ImportHelperDialogFragment
                                               .newInstance(helper)
                                               .show(getChildFragmentManager(),
                                                     ImportHelperDialogFragment.TAG))
                    .create()
                    .show();

        } else {
            // Show a quick-options dialog first.
            // The user can divert to the full options dialog if needed.
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.lbl_import_from_archive)
                    .setMessage(R.string.info_import_option_all_books)
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                    .setNeutralButton(R.string.btn_options,
                                      (dialog, which) -> ImportHelperDialogFragment
                                              .newInstance(helper)
                                              .show(getChildFragmentManager(),
                                                    ImportHelperDialogFragment.TAG))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> mImportModel
                            .startArchiveImportTask(helper))
                    .create()
                    .show();
        }
    }

    /**
     * Import finished/failed: Step 1: Process the result.
     *
     * @param message to process
     */
    private void onImportFinished(
            @NonNull final TaskListener.FinishMessage<ImportManager> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.taskId) {
            case R.id.TASK_ID_READ_FROM_ARCHIVE:
            case R.id.TASK_ID_IMPORTER: {
                switch (message.status) {
                    case Success: {
                        onImportFinished(R.string.progress_end_import_complete,
                                         message.result.getOptions(),
                                         message.result.getResults());
                        break;
                    }
                    case Cancelled: {
                        onImportFinished(R.string.progress_end_import_partially_complete,
                                         message.result.getOptions(),
                                         message.result.getResults());
                        break;
                    }
                    case Failed: {
                        onImportFailed(message.exception);
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

    /**
     * Import finished: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports success or cancelled.
     * @param options what was actually imported
     * @param results what was imported
     */
    private void onImportFinished(@StringRes final int titleId,
                                  final int options,
                                  @NonNull final ImportResults results) {

        // Transform the result data into a user friendly report.
        final StringBuilder msg = new StringBuilder();

        //TODO: LTR
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
        if (results.styles > 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if (results.preferences > 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }

        int failed = results.failedLinesNr.size();
        if (failed > 0) {
            final int fs;
            final Collection<String> msgList = new ArrayList<>();

            if (failed > 10) {
                // keep it sensible, list maximum 10 lines.
                failed = 10;
                fs = R.string.warning_import_csv_failed_lines_lots;
            } else {
                fs = R.string.warning_import_csv_failed_lines_some;
            }
            for (int i = 0; i < failed; i++) {
                msgList.add(getString(R.string.a_bracket_b_bracket,
                                      String.valueOf(results.failedLinesNr.get(i)),
                                      results.failedLinesMessage.get(i)));
            }

            //noinspection ConstantConditions
            msg.append("\n").append(getString(fs, Csv.textList(getContext(), msgList, null)));
        }

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(titleId)
                .setMessage(msg)
                .setPositiveButton(R.string.done, (dialog, which) -> {
                    mResultDataModel.putResultData(UniqueId.BKEY_IMPORT_RESULT, options);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK,
                                            mResultDataModel.getResultData());
                    getActivity().finish();
                })
                .create()
                .show();
    }

    /**
     * Import failed: Step 2: Inform the user.
     *
     * @param e the Exception as returned from the import task
     */
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
    /* Export */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Export to archive: Step 1: show the options to the user.
     */
    private void exportShowOptions() {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_backup_to_archive)
                .setMessage(R.string.info_export_backup_all)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setNeutralButton(R.string.btn_options, (dialog, which)
                        -> ExportHelperDialogFragment.newInstance()
                                                     .show(getChildFragmentManager(),
                                                           ExportHelperDialogFragment.TAG))
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        exportPickUri(new ExportManager(Options.ALL)))
                .create()
                .show();
    }

    /**
     * Export to archive: Step 2: prompt the user for a uri to export to.
     *
     * @param helper export configuration
     */
    private void exportPickUri(@NonNull final ExportManager helper) {
        // save the configured helper
        mExportModel.setHelper(helper);
        //noinspection ConstantConditions
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_TITLE, mExportModel.getDefaultArchiveName(getContext()));
        startActivityForResult(intent, REQ_PICK_FILE_FOR_EXPORT_ARCHIVE);
    }

    /**
     * Export to CSV: Step 1: prompt the user for a uri to export to.
     */
    private void exportCsvPickUri(@NonNull final ExportManager helper) {
        // save the configured helper
        mExportModel.setHelper(helper);
        //noinspection ConstantConditions
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, mExportModel.getDefaultCsvFilename(getContext()));
        startActivityForResult(intent, REQ_PICK_FILE_FOR_EXPORT_CSV);
    }

    /**
     * Export finished/failed: Step 1: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(
            @NonNull final TaskListener.FinishMessage<ExportManager> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.taskId) {
            case R.id.TASK_ID_WRITE_TO_ARCHIVE: {
                switch (message.status) {
                    case Success: {
                        onExportFinished(message.result.getOptions(), message.result.getResults(),
                                         message.result.getUri(), false);
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
            case R.id.TASK_ID_EXPORTER: {
                switch (message.status) {
                    case Success: {
                        onExportFinished(message.result.getOptions(), message.result.getResults(),
                                         message.result.getUri(), true);
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

    /**
     * Export finished: Step 2: Inform the user.
     */
    private void onExportFinished(final int options,
                                  @NonNull final ExportResults results,
                                  @NonNull final Uri uri,
                                  final boolean offerEmail) {
        // Transform the result data into a user friendly report.
        final StringBuilder msg = new StringBuilder();

        //TODO: LTR
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

        if (results.styles > 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if (results.preferences > 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }

        if (offerEmail) {
            //TODO: consider always offering to email if the size is below certain amount.
            msg.append("\n\n").append(getString(R.string.confirm_email_export));
        }
        // The below works, but we cannot get the folder name for the file.
        // Disabling for now. We'd need to change the descriptive string not to include the folder.
        else {
            //noinspection ConstantConditions
            final Pair<String, Long> uriInfo = FileUtils.getUriInfo(getContext(), uri);
            if (uriInfo != null && uriInfo.first != null && uriInfo.second != null) {
                msg.append("\n\n")
                   .append(getString(R.string.X_export_info_success_archive_details,
                                     "",
                                     uriInfo.first,
                                     FileUtils.formatFileSize(getContext(), uriInfo.second)));
            }
        }

        //noinspection ConstantConditions
        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.progress_end_backup_success)
                .setMessage(msg)
                .setPositiveButton(R.string.done, (d, which) -> {
                    mResultDataModel.putResultData(UniqueId.BKEY_EXPORT_RESULT, options);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mResultDataModel.getResultData());
                    getActivity().finish();
                })
                .create();

        if (offerEmail) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.btn_email),
                             (d, which) -> emailExportFile(uri));
        }

        dialog.show();
    }

    /**
     * Export failed: Step 2: Inform the user.
     *
     * @param e the Exception as returned from the export task
     */
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
     * Database export: Step 1: prompt the user for a uri to export to.
     */
    private void exportDatabasePickUri() {
        //noinspection ConstantConditions
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_TITLE, mExportModel.getDefaultDatabaseName(getContext()));
        startActivityForResult(intent, REQ_PICK_FILE_FOR_EXPORT_DATABASE);
    }

    /**
     * Database export: Step 2: Export the actual database file(s).
     *
     * @param uri folder to write to
     */
    private void exportDatabase(@NonNull final Uri uri) {
        @StringRes
        int msgId;
        try {
            final Context context = getContext();
            //noinspection ConstantConditions
            FileUtils.copy(context, DBHelper.getDatabasePath(context), uri);
            msgId = R.string.progress_end_backup_success;
        } catch (@NonNull final IOException e) {
            Logger.error(getContext(), TAG, e);
            msgId = R.string.error_backup_failed;
        }
        //noinspection ConstantConditions
        Snackbar.make(getView(), msgId, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Create and send an email with the specified Uri.
     *
     * @param uri for the file to email
     */
    private void emailExportFile(@NonNull final Uri uri) {

        final String subject = '[' + getString(R.string.app_name) + "] "
                               + getString(R.string.lbl_books);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
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
