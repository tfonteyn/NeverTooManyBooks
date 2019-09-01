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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertoomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvExporter;
import com.hardbacknutter.nevertoomanybooks.backup.csv.ExportCSVTask;
import com.hardbacknutter.nevertoomanybooks.backup.csv.ImportCSVTask;
import com.hardbacknutter.nevertoomanybooks.backup.ui.BackupActivity;
import com.hardbacknutter.nevertoomanybooks.backup.ui.RestoreActivity;
import com.hardbacknutter.nevertoomanybooks.database.CoversDAO;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.FilePicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.ValuePicker;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.TaskQueueListActivity;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTasks;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.ImportTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendBooksTask;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskFinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener.TaskProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.FormattedMessageException;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.IntegerTaskModel;

public class AdminFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "AdminFragment";

    /** requestCode for making a backup to archive. */
    private static final int REQ_ARCHIVE_BACKUP = 0;
    /** requestCode for doing a restore/import from archive. */
    private static final int REQ_ARCHIVE_RESTORE = 1;

    /**
     * collected results from all started activities, which we'll pass on up in our own setResult.
     */
    private final Intent mResultData = new Intent();

    private ProgressDialogFragment mProgressDialog;

    /** ViewModel for task control. */
    private IntegerTaskModel mModel;

    private void onTaskCancelledMessage(
            @SuppressWarnings("unused") @NonNull final TaskFinishedMessage<Integer> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        //noinspection ConstantConditions
        UserMessage.show(getView(), R.string.progress_end_cancelled);
    }

    private void onTaskProgressMessage(@NonNull final TaskProgressMessage message) {
        if (mProgressDialog != null) {
            mProgressDialog.onProgress(message);
        }
    }

    private void onTaskFinishedMessage(@NonNull final TaskFinishedMessage<Integer> message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        //noinspection ConstantConditions
        @NonNull
        View view = getView();

        switch (message.taskId) {
            case R.id.TASK_ID_CSV_EXPORT:
                if (message.wasSuccessful) {
                    onExportFinished();
                } else if (message.exception != null) {
                    UserMessage.show(view, message.exception.getLocalizedMessage());
                }
                break;

            case R.id.TASK_ID_CSV_IMPORT:
                if (!message.wasSuccessful) {
                    String msg;
                    if (message.exception instanceof FormattedMessageException) {
                        //noinspection ConstantConditions
                        msg = ((FormattedMessageException) message.exception)
                                      .getLocalizedMessage(getContext());
                    } else if (message.exception != null) {
                        msg = message.exception.getLocalizedMessage();
                    } else {
                        msg = getString(R.string.error_import_failed);
                    }
                    UserMessage.show(view, msg);
                }
                break;

            case R.id.TASK_ID_GR_IMPORT:
            case R.id.TASK_ID_GR_SEND_BOOKS:
            case R.id.TASK_ID_GR_REQUEST_AUTH:
                //noinspection ConstantConditions
                String msg = GoodreadsTasks.handleResult(getContext(), message);
                if (msg != null) {
                    UserMessage.show(view, msg);
                } else {
                    RequestAuthTask.needsRegistration(getContext(), mModel.getTaskListener());
                }
                break;

            default:
                Logger.warnWithStackTrace(this, "taskId=" + message.taskId);
                break;
        }
    }

    /**
     * Export all data to a CSV file.
     */
    private void exportToCSV() {
        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_backing_up, false, 0);
            mProgressDialog.show(fm, TAG);
            //noinspection ConstantConditions
            ExportCSVTask task = new ExportCSVTask(getContext(),
                                                   new ExportOptions(ExportOptions.BOOK_CSV),
                                                   mModel.getTaskListener());
            mModel.setTask(task);
            task.execute();
        }
        mProgressDialog.setTask(mModel.getTask());
    }

    /**
     * Import all data from somewhere on Shared Storage; ask user to disambiguate if necessary.
     */
    private void importFromCSV() {
        List<File> files = StorageUtils.findCsvFiles();
        // If none, exit with message
        if (files.isEmpty()) {
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.import_error_csv_file_not_found);
        } else {
            if (files.size() == 1) {
                // If only one file found, just use it
                importFromCSV(files.get(0));
            } else {
                // If more than one, ask user which file
                //noinspection ConstantConditions
                ValuePicker picker =
                        new FilePicker(getContext(),
                                       getString(R.string.lbl_import_from_csv),
                                       getString(R.string.import_warning_select_csv_file),
                                       files,
                                       this::importFromCSV);
                picker.show();
            }
        }
    }

    /**
     * Import data.
     *
     * @param file the CSV file to read
     */
    private void importFromCSV(@NonNull final File file) {
        ImportOptions settings = new ImportOptions(ImportOptions.BOOK_CSV);

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
                        settings.what |= ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED;
                    }

                    FragmentManager fm = getChildFragmentManager();
                    mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
                    if (mProgressDialog == null) {
                        mProgressDialog = ProgressDialogFragment.newInstance(
                                R.string.progress_msg_importing, false, 0);
                        mProgressDialog.show(fm, TAG);

                        ImportCSVTask task = new ImportCSVTask(getContext(), file, settings,
                                                               mModel.getTaskListener());
                        mModel.setTask(task);
                        task.execute();
                    }
                    mProgressDialog.setTask(mModel.getTask());
                })
                .create()
                .show();
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
                if (resultCode == Activity.RESULT_OK) {
                    // no local action needed, pass results up
                    //noinspection ConstantConditions
                    getActivity().setResult(resultCode, mResultData);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mModel = new ViewModelProvider(this).get(IntegerTaskModel.class);
        mModel.getTaskFinishedMessage().observe(this, this::onTaskFinishedMessage);
        mModel.getTaskProgressMessage().observe(this, this::onTaskProgressMessage);
        mModel.getTaskCancelledMessage().observe(this, this::onTaskCancelledMessage);

        FragmentManager fm = getChildFragmentManager();
        mProgressDialog = (ProgressDialogFragment) fm.findFragmentByTag(TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTask(mModel.getTask());
        }

        View root = getView();

        // Export (backup) to Archive
        //noinspection ConstantConditions
        root.findViewById(R.id.lbl_backup)
            .setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), BackupActivity.class);
                startActivityForResult(intent, REQ_ARCHIVE_BACKUP);
            });

        // Import from Archive - Start the restore activity
        root.findViewById(R.id.lbl_import_from_archive)
            .setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), RestoreActivity.class);
                startActivityForResult(intent, REQ_ARCHIVE_RESTORE);
            });

        // Export to CSV
        root.findViewById(R.id.lbl_export)
            .setOnClickListener(v -> exportToCSV());

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
                        .setPositiveButton(android.R.string.ok, (d, which) -> importFromCSV())
                        .create()
                        .show();
            });


        // Goodreads Import Synchronize
        root.findViewById(R.id.lbl_sync_with_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new ImportTask(getContext(), true, mModel.getTaskListener()).execute();
            });

        // Goodreads Import All
        root.findViewById(R.id.lbl_import_all_from_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new ImportTask(getContext(), false, mModel.getTaskListener()).execute();
            });

        // Goodreads Export Updated
        root.findViewById(R.id.lbl_send_updated_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new SendBooksTask(getContext(), true, mModel.getTaskListener()).execute();
            });

        // Goodreads Export All
        root.findViewById(R.id.lbl_send_all_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.show(v, R.string.progress_msg_connecting);
                //noinspection ConstantConditions
                new SendBooksTask(getContext(), false, mModel.getTaskListener()).execute();
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

        /* Copy database for tech support */
        root.findViewById(R.id.lbl_copy_database)
            .setOnClickListener(v -> {
                //noinspection ConstantConditions
                StorageUtils.exportDatabaseFiles(getContext());
                UserMessage.show(v, R.string.progress_end_backup_success);
            });
    }

    /**
     * Callback for the CSV export task.
     */
    private void onExportFinished() {
        if (isResumed()) {
            //noinspection ConstantConditions
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.export_csv_email)
                    .setIcon(R.drawable.ic_send)
                    .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                    .setPositiveButton(android.R.string.ok, (d, which) -> emailCSVFile())
                    .create()
                    .show();
        }
    }

    /**
     * Create and send an email with the CSV export file.
     */
    private void emailCSVFile() {
        String subject = '[' + getString(R.string.app_name) + "] "
                         + getString(R.string.lbl_export_to_csv);

        final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                                      .setType("plain/text")
                                      .putExtra(Intent.EXTRA_SUBJECT, subject);

        ArrayList<Uri> uris = new ArrayList<>();
        try {
            File csvExportFile = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
            @SuppressWarnings("ConstantConditions")
            Uri coverURI = FileProvider.getUriForFile(getContext(),
                                                      GenericFileProvider.AUTHORITY,
                                                      csvExportFile);

            uris.add(coverURI);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, getString(R.string.title_send_mail)));
        } catch (@NonNull final NullPointerException e) {
            Logger.error(this, e);
            //noinspection ConstantConditions
            UserMessage.show(getView(), R.string.error_email_failed);
        }
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
}
