package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.backup.ExportOptions;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.backup.ImportOptions;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.csv.ExportCSVTask;
import com.eleybourn.bookcatalogue.backup.csv.ImportCSVTask;
import com.eleybourn.bookcatalogue.backup.ui.BackupActivity;
import com.eleybourn.bookcatalogue.backup.ui.RestoreActivity;
import com.eleybourn.bookcatalogue.database.CoversDAO;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.FilePicker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.ValuePicker;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.TaskQueueListActivity;
import com.eleybourn.bookcatalogue.goodreads.tasks.GoodreadsTasks;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.tasks.TaskListener;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public class AdminFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = AdminFragment.class.getSimpleName();

    /** requestCode for making a backup to archive. */
    private static final int REQ_ARCHIVE_BACKUP = 0;
    /** requestCode for doing a restore/import from archive. */
    private static final int REQ_ARCHIVE_RESTORE = 1;

    /**
     * collected results from all started activities, which we'll pass on up in our own setResult.
     */
    private final Intent mResultData = new Intent();
    private final TaskListener<Object, Integer> mListener = new TaskListener<Object, Integer>() {
        /**
         * The result of the task is not used here.
         * <p>
         * <br>{@inheritDoc}
         */
        @Override
        public void onTaskFinished(final int taskId,
                                   final boolean success,
                                   @Nullable final Integer result,
                                   @Nullable final Exception e) {
            switch (taskId) {
                case R.id.TASK_ID_CSV_EXPORT:
                    if (success) {
                        onExportFinished();
                    } else if (e != null) {
                        UserMessage.showUserMessage(requireView(), e.getLocalizedMessage());
                    }
                    break;

                case R.id.TASK_ID_CSV_IMPORT:
                    if (!success) {
                        String msg;
                        if (e instanceof FormattedMessageException) {
                            //noinspection ConstantConditions
                            msg = ((FormattedMessageException) e).getFormattedMessage(getContext());
                        } else if (e != null) {
                            msg = e.getLocalizedMessage();
                        } else {
                            msg = getString(R.string.error_import_failed);
                        }
                        UserMessage.showUserMessage(requireView(), msg);
                    }
                    break;

                case R.id.TASK_ID_GR_IMPORT:
                case R.id.TASK_ID_GR_SEND_BOOKS:
                case R.id.TASK_ID_GR_REQUEST_AUTH:
                    GoodreadsTasks.handleGoodreadsTaskResult(taskId, success, result, e,
                                                             requireView(), this);
                    break;
            }
        }
    };
    private ProgressDialogFragment<Object, Integer> mProgressDialog;

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

        FragmentManager fm = getChildFragmentManager();
        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, Integer>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog != null) {
            mProgressDialog.setTaskListener(mListener);
//            mProgressDialog.setOnUserCancelledListener(this);
        }

        View root = requireView();

        // Export (backup) to Archive
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
                        .setMessage(R.string.warning_import_be_cautious)
                        .setTitle(R.string.title_import_book_data)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, which) -> importFromCSV())
                        .create()
                        .show();
            });


        // Goodreads Import Synchronize
        root.findViewById(R.id.lbl_sync_with_goodreads)
            .setOnClickListener(v -> {
                UserMessage.showUserMessage(v, R.string.progress_msg_connecting);
                new GoodreadsTasks.ImportTask(true, mListener).execute();
            });

        // Goodreads Import All
        root.findViewById(R.id.lbl_import_all_from_goodreads)
            .setOnClickListener(v -> {
                UserMessage.showUserMessage(v, R.string.progress_msg_connecting);
                new GoodreadsTasks.ImportTask(false, mListener).execute();
            });

        // Goodreads Export Updated
        root.findViewById(R.id.lbl_send_updated_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.showUserMessage(v, R.string.progress_msg_connecting);
                new GoodreadsTasks.SendBooksTask(true, mListener).execute();
            });

        // Goodreads Export All
        root.findViewById(R.id.lbl_send_all_books_to_goodreads)
            .setOnClickListener(v -> {
                UserMessage.showUserMessage(v, R.string.progress_msg_connecting);
                new GoodreadsTasks.SendBooksTask(false, mListener).execute();
            });

        /* Start the activity that shows the active GoodReads tasks. */
        root.findViewById(R.id.lbl_background_tasks)
            .setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), TaskQueueListActivity.class);
                startActivity(intent);
            });



        /* Automatically Update Fields from internet*/
        root.findViewById(R.id.lbl_update_internet)
            .setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), UpdateFieldsFromInternetActivity.class);
                startActivity(intent);
            });

        /* Reset Hints */
        root.findViewById(R.id.lbl_reset_hints)
            .setOnClickListener(v -> {
                HintManager.resetHints();
                UserMessage.showUserMessage(v, R.string.hints_have_been_reset);
            });

        /* Erase cover cache */
        root.findViewById(R.id.lbl_erase_cover_cache)
            .setOnClickListener(v -> {
                try (CoversDAO coversDBAdapter = CoversDAO.getInstance()) {
                    coversDBAdapter.deleteAll();
                }
            });

        /* Copy database for tech support */
        root.findViewById(R.id.lbl_copy_database)
            .setOnClickListener(v -> {
                StorageUtils.exportDatabaseFiles();
                UserMessage.showUserMessage(v, R.string.progress_end_backup_success);
            });
    }

    /**
     * Export all data to a CSV file.
     */
    private void exportToCSV() {
        File file = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
        ExportOptions settings = new ExportOptions(file);
        settings.what = ExportOptions.BOOK_CSV;

        FragmentManager fm = getChildFragmentManager();
        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, Integer>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_backing_up, false, 0);
            ExportCSVTask task = new ExportCSVTask(settings, mProgressDialog);
            mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            task.execute();
        }
        mProgressDialog.setTaskListener(mListener);
        //mProgressDialog.setOnUserCancelledListener(this);
    }

    /**
     * Import all data from somewhere on Shared Storage; ask user to disambiguate if necessary.
     */
    private void importFromCSV() {
        List<File> files = StorageUtils.findCsvFiles();
        // If none, exit with message
        if (files.isEmpty()) {
            UserMessage.showUserMessage(requireView(), R.string.import_error_csv_file_not_found);
        } else {
            if (files.size() == 1) {
                // If only 1, just use it
                importFromCSV(files.get(0));
            } else {
                // If more than one, ask user which file
                // ENHANCE: Consider asking about importing cover images.
                @SuppressWarnings("ConstantConditions")
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
        ImportOptions settings = new ImportOptions(file);
        settings.what = ImportOptions.BOOK_CSV;

        FragmentManager fm = getChildFragmentManager();
        //noinspection unchecked
        mProgressDialog = (ProgressDialogFragment<Object, Integer>)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialogFragment.newInstance(
                    R.string.progress_msg_importing, false, 0);
            ImportCSVTask task = new ImportCSVTask(settings, mProgressDialog);
            mProgressDialog.show(fm, ProgressDialogFragment.TAG);
            task.execute();
        }
        mProgressDialog.setTaskListener(mListener);
        //mProgressDialog.setOnUserCancelledListener(this);
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

    /**
     * Callback for the CSV export task.
     */
    private void onExportFinished() {
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.export_csv_email)
                .setIcon(R.drawable.ic_send)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> emailCSVFile())
                .create()
                .show();
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
        } catch (NullPointerException e) {
            Logger.error(this, e);
            UserMessage.showUserMessage(requireView(), R.string.error_email_failed);
        }
    }
}
