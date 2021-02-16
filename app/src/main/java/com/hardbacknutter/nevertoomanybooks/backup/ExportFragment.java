/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriterTask;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreContentServerWriter;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentExportBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class ExportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ExportFragment";
    public static final String BKEY_ENCODING = TAG + ":encoding";
    /** The maximum file size for an export file for which we'll offer to send it as an email. */
    private static final int MAX_FILE_SIZE_FOR_EMAIL = 5_000_000;

    /**
     * The ViewModel and the {@link #mArchiveWriterTask} could be folded into one object,
     * but we're trying to keep task logic separate for now.
     */
    private ExportViewModel mVm;
    private ArchiveWriterTask mArchiveWriterTask;
    /** The launcher for picking a Uri to write to. */
    private final ActivityResultLauncher<String> mCreateDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(),
                                      this::exportToUri);
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    private Toolbar mToolbar;
    /** View Binding. */
    private FragmentExportBinding mVb;
    @Nullable
    private ArchiveEncoding mPresetEncoding;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        final Bundle args = getArguments();
        if (args != null) {
            mPresetEncoding = args.getParcelable(BKEY_ENCODING);
        }
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
        mToolbar = getActivity().findViewById(R.id.toolbar);

        mVm = new ViewModelProvider(getActivity()).get(ExportViewModel.class);

        mArchiveWriterTask = new ViewModelProvider(this).get(ArchiveWriterTask.class);
        mArchiveWriterTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mArchiveWriterTask.onCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        mArchiveWriterTask.onFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        mArchiveWriterTask.onFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        // if the task is NOT already running (e.g. after a screen rotation...) ...
        if (!mArchiveWriterTask.isRunning()) {
            // show either the full details screen or the quick options dialog
            if (mVm.isQuickOptionsAlreadyShown()) {
                showOptions();
            } else {
                mVm.setQuickOptionsAlreadyShown(true);
                showQuickOptions();
            }
        }
    }

    private void showQuickOptions() {
        final ExportHelper helper = mVm.getExportHelper();

        if (mPresetEncoding != null && mPresetEncoding.isRemoteServer()) {
            mToolbar.setTitle(R.string.action_export);
            helper.setEncoding(mPresetEncoding);

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(mPresetEncoding.getRemoteServerDescriptionResId())
                    .setMessage(R.string.action_synchronize)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                    .setNeutralButton(R.string.btn_options, (d, w) -> {
                        d.dismiss();
                        showOptions();
                    })
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        d.dismiss();
                        // WARNING: hardcoded for now as we only have this one.
                        exportToCalibre();
                    })
                    .create()
                    .show();
        } else {
            mToolbar.setTitle(R.string.lbl_backup);
            helper.setEncoding(ArchiveEncoding.Zip);

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.lbl_backup)
                    .setMessage(R.string.txt_export_backup_all)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                    .setNeutralButton(R.string.btn_options, (d, w) -> {
                        d.dismiss();
                        showOptions();
                    })
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        d.dismiss();
                        exportPickUri();
                    })
                    .create()
                    .show();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_action_go, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (mVm.getExportHelper().getExporterEntries().size() > 1) {
                if (mPresetEncoding != null && mPresetEncoding.isRemoteServer()) {
                    // WARNING: hardcoded for now as we only have this one.
                    exportToCalibre();
                } else {
                    exportPickUri();
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Export Step 1b: Show the full options screen to the user.
     */
    private void showOptions() {
        final ExportHelper helper = mVm.getExportHelper();
        final Set<RecordType> exportEntities = helper.getExporterEntries();

        mVb.cbxCovers.setChecked(exportEntities.contains(RecordType.Cover));
        mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                .setExportEntry(RecordType.Cover, isChecked));

        final boolean incremental = helper.isIncremental();
        mVb.rbExportBooksOptionAll.setChecked(!incremental);
        mVb.rbExportBooksOptionNewAndUpdated.setChecked(incremental);
        mVb.rbExportBooksOptionNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);
        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> helper
                .setIncremental(checkedId == mVb.rbExportBooksOptionNewAndUpdated.getId()));

        if (mPresetEncoding != null && mPresetEncoding.isRemoteServer()) {
            mToolbar.setTitle(R.string.action_export);
            helper.setEncoding(mPresetEncoding);

            mVb.cbxBooks.setChecked(true);
            mVb.cbxBooks.setEnabled(true);

            mVb.rbExportBooksOptionNewAndUpdated.setChecked(true);

            mVb.lblArchiveFormat.setVisibility(View.GONE);
            mVb.archiveFormat.setVisibility(View.GONE);
            mVb.archiveFormatInfo.setVisibility(View.GONE);
            mVb.archiveFormatInfoLong.setVisibility(View.GONE);

            mVb.lblRemoteServer.setVisibility(View.VISIBLE);
            mVb.lblRemoteServer.setText(mPresetEncoding.getRemoteServerDescriptionResId());
            mVb.cbxDeleteRemovedBooks.setVisibility(View.VISIBLE);
            mVb.cbxDeleteRemovedBooks.setOnCheckedChangeListener(
                    (v, isChecked) ->
                            mVm.getExportHelper()
                               .getExtraArgs()
                               .putBoolean(CalibreContentServerWriter.BKEY_DELETE_LOCAL_BOOKS,
                                           isChecked));

        } else {
            mVb.cbxBooks.setChecked(exportEntities.contains(RecordType.Books));
            mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mVm.getExportHelper().setExportEntry(RecordType.Books, isChecked);
                mVb.rbBooksGroup.setEnabled(isChecked);
            });

            //noinspection ConstantConditions
            final Pair<Integer, ArrayList<String>> fo = mVm.getFormatOptions(getContext());
            final int initialPos = fo.first;
            final ArrayList<String> list = fo.second;

            final ExtArrayAdapter<String> archiveFormatAdapter = new ExtArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    ExtArrayAdapter.FilterType.Passthrough, list);
            mVb.archiveFormat.setAdapter(archiveFormatAdapter);
            mVb.archiveFormat.setOnItemClickListener(
                    (parent, view, position, id) -> updateFormatSelection(
                            mVm.getEncoding(position)));

            mVb.archiveFormat.setText(list.get(initialPos), false);
            updateFormatSelection(helper.getEncoding());
        }

        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void updateFormatSelection(@NonNull final ArchiveEncoding encoding) {

        final ExportHelper helper = mVm.getExportHelper();

        //noinspection EnumSwitchStatementWhichMissesCases
        switch (encoding) {
            case Zip: {
                mToolbar.setTitle(R.string.lbl_backup);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
                mVb.archiveFormatInfoLong.setText("");

                helper.setEncoding(ArchiveEncoding.Zip);
                helper.setExportEntry(RecordType.Styles, true);
                helper.setExportEntry(RecordType.Preferences, true);
                helper.setExportEntry(RecordType.Certificates, true);

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(true);

                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbExportBooksOptionNewAndUpdated.setChecked(true);

                mVb.cbxCovers.setChecked(true);
                mVb.cbxCovers.setEnabled(true);
                break;
            }
            case Csv: {
                mToolbar.setTitle(R.string.action_export);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_csv_info);
                mVb.archiveFormatInfoLong.setText("");

                helper.setEncoding(ArchiveEncoding.Csv);
                helper.setExportEntry(RecordType.Styles, false);
                helper.setExportEntry(RecordType.Preferences, false);
                helper.setExportEntry(RecordType.Certificates, false);

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);

                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbExportBooksOptionNewAndUpdated.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);
                break;
            }
            case Json: {
                mToolbar.setTitle(R.string.action_export);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_format_json_info);
                mVb.archiveFormatInfoLong.setText("");

                helper.setEncoding(ArchiveEncoding.Json);
                helper.setExportEntry(RecordType.Styles, false);
                helper.setExportEntry(RecordType.Preferences, false);
                helper.setExportEntry(RecordType.Certificates, false);


                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);

                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbExportBooksOptionAll.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);
                break;
            }
            case Xml: {
                mToolbar.setTitle(R.string.action_export);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_format_xml_info);
                mVb.archiveFormatInfoLong.setText(R.string.lbl_archive_is_export_only);

                helper.setEncoding(ArchiveEncoding.Xml);
                helper.setExportEntry(RecordType.Styles, false);
                helper.setExportEntry(RecordType.Preferences, false);
                helper.setExportEntry(RecordType.Certificates, false);


                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);

                // See class docs for XmlArchiveWriter
                mVb.rbBooksGroup.setEnabled(false);
                mVb.rbExportBooksOptionAll.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);
                break;
            }
            case SqLiteDb: {
                mToolbar.setTitle(R.string.action_export);
                mVb.archiveFormatInfo.setText(R.string.lbl_archive_format_db_info);
                mVb.archiveFormatInfoLong.setText(R.string.lbl_archive_is_export_only);

                helper.setEncoding(ArchiveEncoding.SqLiteDb);
                helper.setExportEntry(RecordType.Styles, false);
                helper.setExportEntry(RecordType.Preferences, false);
                helper.setExportEntry(RecordType.Certificates, false);


                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);

                mVb.rbBooksGroup.setEnabled(false);
                mVb.rbExportBooksOptionAll.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);
                break;
            }
            default:
                throw new IllegalArgumentException(encoding.toString());
        }
    }

    /**
     * Export Step 2: prompt the user for a uri to export to.
     * After picking, we continue in {@link #exportToUri(Uri)}.
     */
    private void exportPickUri() {
        // Create the proposed name for the archive. The user can change it.
        final String defName = "ntmb-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                               + mVm.getExportHelper().getEncoding().getFileExt();
        mCreateDocumentLauncher.launch(defName);
    }

    /**
     * Export Step 3: Called after the user selected a uri to write to.
     *
     * @param uri to write to
     */
    private void exportToUri(@Nullable final Uri uri) {
        if (uri != null) {
            final ExportHelper helper = mVm.getExportHelper();
            helper.setUri(uri);
            mArchiveWriterTask.start(helper);
        }
    }

    private void exportToCalibre() {
        final ExportHelper helper = mVm.getExportHelper();
        helper.setExportEntry(RecordType.Styles, false);
        helper.setExportEntry(RecordType.Preferences, false);
        helper.setExportEntry(RecordType.Certificates, false);


        //noinspection ConstantConditions
        helper.setUri(Uri.parse(CalibreContentServer.getHostUrl(getContext())));
        mArchiveWriterTask.start(helper);
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
                    getString(R.string.menu_backup_and_export), false, true);
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
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
        }
    }

    private void onExportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            @StringRes
            final int title = mVm.getExportHelper().isBackup()
                              ? R.string.error_backup_failed
                              : R.string.error_export_failed;

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(title)
                    .setMessage(Backup.createErrorReport(getContext(), message.result))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
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

            final List<String> items = extractExportedItems(message.result);
            if (items.isEmpty()) {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.menu_backup_and_export)
                        .setMessage(R.string.warning_no_matching_book_found)
                        .setPositiveButton(R.string.action_done, (d, w)
                                -> getActivity().finish())
                        .create()
                        .show();
            } else {

                final String itemList = items
                        .stream()
                        .map(s -> getString(R.string.list_element, s))
                        .collect(Collectors.joining("\n"));

                final ExportHelper helper = mVm.getExportHelper();

                if (helper.getEncoding().isRemoteServer()) {
                    //noinspection ConstantConditions
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_baseline_info_24)
                            .setTitle(R.string.progress_end_export_successful)
                            .setMessage(itemList)
                            .setPositiveButton(R.string.action_done, (d, w)
                                    -> getActivity().finish())
                            .create()
                            .show();
                } else {

                    @StringRes
                    final int title = helper.isBackup() ? R.string.progress_end_backup_successful
                                                        : R.string.progress_end_export_successful;

                    //noinspection ConstantConditions
                    final MaterialAlertDialogBuilder dialogBuilder =
                            new MaterialAlertDialogBuilder(getContext())
                                    .setIcon(R.drawable.ic_baseline_info_24)
                                    .setTitle(title)
                                    .setPositiveButton(R.string.action_done, (d, w)
                                            -> getActivity().finish());

                    final StringBuilder msg = new StringBuilder(itemList);

                    final FileUtils.UriInfo uriInfo = helper.getUriInfo(getContext());
                    final long size = uriInfo.getSize();

                    // We cannot get the folder name for the file.
                    // FIXME: We need to change the descriptive string not to include the folder.
                    msg.append("\n\n")
                       .append(getString(R.string.progress_end_export_report, "",
                                         uriInfo.getDisplayName(),
                                         FileUtils.formatFileSize(getContext(), size)));

                    if (size > 0 && size < MAX_FILE_SIZE_FOR_EMAIL) {
                        msg.append("\n\n").append(getString(R.string.confirm_email_file));

                        dialogBuilder.setNeutralButton(R.string.btn_email, (d, w) ->
                                onExportEmail(uriInfo.getUri(), itemList));
                    }

                    dialogBuilder.setMessage(msg)
                                 .create()
                                 .show();
                }
            }
        }
    }

    @NonNull
    private List<String> extractExportedItems(
            @NonNull final ExportResults result) {
        final List<String> items = new ArrayList<>();

        if (result.getBookCount() > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_books),
                                String.valueOf(result.getBookCount())));
        }
        if (result.getCoverCount() > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_covers),
                                String.valueOf(result.getCoverCount())));
        }

        if (result.styles > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_styles),
                                String.valueOf(result.styles)));
        }
        if (result.preferences > 0) {
            items.add(getString(R.string.lbl_settings));
        }
        if (result.certificates > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_certificates),
                                String.valueOf(result.certificates)));
        }
        if (result.database) {
            items.add(getString(R.string.lbl_database));
        }
        return items;
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
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setMessage(R.string.error_email_failed)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

}
