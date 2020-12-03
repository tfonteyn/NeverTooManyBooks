/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.HostingActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterRecord;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterTask;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentExportBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * This fragment is a blank screen and all actions are done using dialogs (fullscreen and actual).
 */
public class ExportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ExportFragment";
    /** The maximum file size for an export file for which we'll offer to send it as an email. */
    private static final int MAX_FILE_SIZE_FOR_EMAIL = 5_000_000;

    /**
     * The ViewModel and the {@link #mArchiveWriterTask} could be folded into one object,
     * but we're trying to keep task logic separate for now.
     */
    private ExportViewModel mExportViewModel;

    private ArchiveWriterTask mArchiveWriterTask;
    /** The launcher for picking a Uri to write to. */
    private final ActivityResultLauncher<String> mCreateDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(),
                                      this::onCreateDocument);
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** View Binding. */
    private FragmentExportBinding mVb;


    /** prevent first-time {@link AdapterView.OnItemSelectedListener#onItemSelected} call. */
    private boolean mArchiveFormatIsSet;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentExportBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_backup);

        mExportViewModel = new ViewModelProvider(getActivity()).get(ExportViewModel.class);

        mArchiveWriterTask = new ViewModelProvider(this).get(ArchiveWriterTask.class);
        mArchiveWriterTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mArchiveWriterTask.onCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        mArchiveWriterTask.onFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        mArchiveWriterTask.onFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        // if the task is NOT already running (e.g. after a screen rotation...) show the options
        if (!mArchiveWriterTask.isRunning()) {
            showQuickOptions();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_export, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (!mExportViewModel.getExportHelper().getExporterEntries().isEmpty()) {
                exportPickUri();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Export Step 1: show the quick/simple options dialog to the user.
     */
    private void showQuickOptions() {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.txt_export_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                .setNeutralButton(R.string.btn_options, (d, w) -> {
                    d.dismiss();
                    showScreen();
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    d.dismiss();
                    exportPickUri();
                })
                .create()
                .show();
    }

    /**
     * Export Step 1b: Show the full options screen to the user.
     */
    private void showScreen() {
        final ExportHelper helper = mExportViewModel.getExportHelper();
        final Set<ArchiveWriterRecord.Type> exportEntities = helper.getExporterEntries();

        mVb.cbxBooks.setChecked(exportEntities.contains(
                ArchiveWriterRecord.Type.Books));
        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setExportEntry(ArchiveWriterRecord.Type.Cover, isChecked);
            mVb.rbBooksGroup.setEnabled(isChecked);
        });

        final boolean incremental = helper.isIncremental();
        mVb.rbBooksAll.setChecked(!incremental);
        mVb.rbBooksIncremental.setChecked(incremental);
        mVb.rbBooksIncrementalInfo.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> helper
                .setIncremental(checkedId == mVb.rbBooksIncremental.getId()));


        mVb.cbxCovers.setChecked(exportEntities.contains(
                ArchiveWriterRecord.Type.Cover));
        mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                .setExportEntry(ArchiveWriterRecord.Type.Cover, isChecked));

        mVb.cbxPrefs.setChecked(exportEntities.contains(
                ArchiveWriterRecord.Type.Preferences));
        mVb.cbxPrefs.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                .setExportEntry(ArchiveWriterRecord.Type.Preferences, isChecked));

        mVb.cbxStyles.setChecked(exportEntities.contains(
                ArchiveWriterRecord.Type.Styles));
        mVb.cbxStyles.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                .setExportEntry(ArchiveWriterRecord.Type.Styles, isChecked));

        //ENHANCE: export from JSON not exposed to the user yet

        // Check options on position.
        final List<String> list = new ArrayList<>();
        list.add(getString(R.string.lbl_archive_type_backup, ArchiveType.Zip.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_backup, ArchiveType.Tar.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_csv, ArchiveType.Csv.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_xml, ArchiveType.Xml.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_db, ArchiveType.SqLiteDb.getFileExt()));

        // The default selection is index 0, ZIP format.
        helper.setArchiveType(ArchiveType.Zip);
        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
        mVb.archiveFormatInfoLong.setText("");

        //noinspection ConstantConditions
        final ArrayAdapter<String> archiveFormatAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, list);
        archiveFormatAdapter.setDropDownViewResource(R.layout.dropdown_menu_popup_item);
        mVb.archiveFormat.setAdapter(archiveFormatAdapter);
        mVb.archiveFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(@NonNull final AdapterView<?> parent,
                                       @NonNull final View view,
                                       final int position,
                                       final long id) {
                if (!mArchiveFormatIsSet) {
                    mArchiveFormatIsSet = true;
                    return;
                }

                updateFromFormatSelection(position);
            }

            @Override
            public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                // Do Nothing
            }
        });

        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void updateFromFormatSelection(final int position) {

        final ExportHelper helper = mExportViewModel.getExportHelper();
        switch (position) {
            case 0: {
                helper.setArchiveType(ArchiveType.Zip);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
                mVb.archiveFormatInfoLong.setText("");

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(true);
                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbBooksIncremental.setChecked(true);

                mVb.cbxCovers.setChecked(true);
                mVb.cbxCovers.setEnabled(true);

                mVb.cbxPrefs.setChecked(true);
                mVb.cbxPrefs.setEnabled(true);

                mVb.cbxStyles.setChecked(true);
                mVb.cbxStyles.setEnabled(true);
                break;
            }
            case 1: {
                helper.setArchiveType(ArchiveType.Tar);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
                mVb.archiveFormatInfoLong.setText("");

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(true);
                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbBooksIncremental.setChecked(true);

                mVb.cbxCovers.setChecked(true);
                mVb.cbxCovers.setEnabled(true);

                mVb.cbxPrefs.setChecked(true);
                mVb.cbxPrefs.setEnabled(true);

                mVb.cbxStyles.setChecked(true);
                mVb.cbxStyles.setEnabled(true);
                break;
            }
            case 2: {
                helper.setArchiveType(ArchiveType.Csv);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_csv_info);
                mVb.archiveFormatInfoLong.setText("");

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);
                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbBooksIncremental.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);

                mVb.cbxPrefs.setChecked(false);
                mVb.cbxPrefs.setEnabled(false);

                mVb.cbxStyles.setChecked(false);
                mVb.cbxStyles.setEnabled(false);
                break;
            }
            case 3: {
                helper.setArchiveType(ArchiveType.Xml);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_format_xml_info);
                mVb.archiveFormatInfoLong.setText(R.string.lbl_archive_is_export_only);

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);
                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbBooksAll.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);

                mVb.cbxPrefs.setChecked(false);
                mVb.cbxPrefs.setEnabled(true);

                mVb.cbxStyles.setChecked(false);
                mVb.cbxStyles.setEnabled(true);
                break;
            }
            case 4: {
                helper.setArchiveType(ArchiveType.SqLiteDb);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_format_db_info);
                mVb.archiveFormatInfoLong.setText(R.string.lbl_archive_is_export_only);

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);
                mVb.rbBooksGroup.setEnabled(false);
                mVb.rbBooksAll.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);

                mVb.cbxPrefs.setChecked(false);
                mVb.cbxPrefs.setEnabled(false);

                mVb.cbxStyles.setChecked(false);
                mVb.cbxStyles.setEnabled(false);
                break;
            }
            default:
                throw new IllegalArgumentException(String.valueOf(position));
        }
    }

    /**
     * Export Step 2: prompt the user for a uri to export to.
     */
    private void exportPickUri() {
        final String defName = mExportViewModel.getExportHelper().getDefaultUriName();
        mCreateDocumentLauncher.launch(defName);
    }

    /**
     * Export Step 3: Called after the user selected a uri to write to.
     *
     * @param uri to write to
     */
    private void onCreateDocument(@Nullable final Uri uri) {
        if (uri != null) {
            final ExportHelper exportHelper = mExportViewModel.getExportHelper();
            exportHelper.setUri(uri);
            mArchiveWriterTask.startExport(exportHelper);
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog();
        }
        mProgressDialog.onProgress(message);
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog() {
        final FragmentManager fm = getChildFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        // not found? create it
        if (dialog == null) {
            dialog = ProgressDialogFragment.newInstance(
                    getString(R.string.lbl_backing_up), false, true);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mArchiveWriterTask);
        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private void onExportCancelled(@NonNull final FinishedMessage<ExportResults> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
        }
    }

    private void onExportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_error)
                    .setTitle(R.string.error_backup_failed)
                    .setMessage(createErrorReport(getContext(), message.result))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    @NonNull
    private String createErrorReport(@NonNull final Context context,
                                     @Nullable final Exception e) {
        String msg = null;

        if (e instanceof IOException) {
            // see if we can find the exact cause
            if (e.getCause() instanceof ErrnoException) {
                final int errno = ((ErrnoException) e.getCause()).errno;
                // write failed: ENOSPC (No space left on device)
                if (errno == OsConstants.ENOSPC) {
                    msg = context.getString(R.string.error_storage_no_space_left);
                } else {
                    // write to logfile for future reporting enhancements.
                    Logger.warn(context, TAG, "onExportFailed|errno=" + errno);
                }
            }

            // generic IOException message
            if (msg == null) {
                msg = StandardDialogs.createBadError(context, R.string.error_storage_not_writable);
            }
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = context.getString(R.string.error_unknown_long);
        }

        return msg;
    }

    /**
     * Export finished/failed: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(@NonNull final FinishedMessage<ExportResults> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            final ExportHelper exportHelper = mExportViewModel.getExportHelper();

            //noinspection ConstantConditions
            final MaterialAlertDialogBuilder dialogBuilder =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_info)
                            .setTitle(R.string.progress_end_backup_success)
                            .setPositiveButton(R.string.done, (d, w) -> getActivity().finish());

            final Uri uri = exportHelper.getUri();
            final FileUtils.UriInfo uriInfo = FileUtils.getUriInfo(getContext(), uri);
            final String report = createReport(uriInfo, message.result);

            if (uriInfo.size > 0 && uriInfo.size < MAX_FILE_SIZE_FOR_EMAIL) {
                dialogBuilder.setMessage(report + "\n\n"
                                         + getString(R.string.confirm_email_export))
                             .setNeutralButton(R.string.btn_email, (d, w) ->
                                     onExportEmail(uri, report));
            } else {
                dialogBuilder.setMessage(report);
            }

            dialogBuilder.create()
                         .show();
        }
    }

    /**
     * Transform the result data into a user friendly report.
     *
     * @param uriInfo name and size of the file saved
     * @param result  to report
     *
     * @return report string
     */
    @NonNull
    private String createReport(@NonNull final FileUtils.UriInfo uriInfo,
                                @NonNull final ExportResults result) {

        final Context context = getContext();
        final StringJoiner report = new StringJoiner("\n", "• ", "");
        report.setEmptyValue("");

        if (!result.getBooksExported().isEmpty()) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.name_colon_value,
                                         context.getString(R.string.lbl_books),
                                         String.valueOf(result.getBooksExported().size())));
        }
        if (!result.getCoverFileNames().isEmpty()) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.name_colon_value,
                                         context.getString(R.string.lbl_covers),
                                         String.valueOf(result.getCoverFileNames().size())));
        }

        if (result.styles > 0) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.name_colon_value,
                                         context.getString(R.string.lbl_styles),
                                         String.valueOf(result.styles)));
        }
        if (result.preferences > 0) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.lbl_settings));
        }
        if (result.database) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.lbl_database));
        }

        // We cannot get the folder name for the file.
        // FIXME: We need to change the descriptive string not to include the folder.
        //noinspection ConstantConditions
        return report.toString() + "\n\n" + context.getString(
                R.string.progress_end_export_success, "",
                uriInfo.displayName, FileUtils.formatFileSize(context, uriInfo.size));
    }

    /**
     * Create and send an email with the specified Uri.
     *
     * @param uri    for the file to email
     * @param report export report text; will be added to the mail body
     */
    private void onExportEmail(@NonNull final Uri uri,
                               final String report) {

        final String subject = '[' + getString(R.string.app_name) + "] "
                               + getString(R.string.lbl_backup);

        final ArrayList<Uri> uriList = new ArrayList<>();
        uriList.add(uri);
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TEXT, report)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
            startActivity(intent);
            //noinspection ConstantConditions
            getActivity().finish();

        } catch (@NonNull final NullPointerException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_error)
                    .setMessage(R.string.error_email_failed)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    public static class ResultContract
            extends ActivityResultContract<Void, Boolean> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @Nullable final Void aVoid) {
            return new Intent(context, HostingActivity.class)
                    .putExtra(HostingActivity.BKEY_FRAGMENT_TAG, ExportFragment.TAG);
        }

        @Override
        @NonNull
        public Boolean parseResult(final int resultCode,
                                   @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            return intent != null && resultCode == Activity.RESULT_OK;
        }
    }
}
