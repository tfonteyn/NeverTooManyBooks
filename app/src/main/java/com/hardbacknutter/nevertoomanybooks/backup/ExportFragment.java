/*
 * @Copyright 2018-2022 HardBackNutter
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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForWritingContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentExportBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UriInfo;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.ExtArrayAdapter;

public class ExportFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "ExportFragment";

    /** The maximum file size for an export file for which we'll offer to send it as an email. */
    private static final int MAX_FILE_SIZE_FOR_EMAIL = 5_000_000;
    /** The ViewModel. */
    private ExportViewModel vm;

    /** The launcher for picking a Uri to write to. */
    private final ActivityResultLauncher<GetContentUriForWritingContract.Input>
            createDocumentLauncher =
            registerForActivityResult(new GetContentUriForWritingContract(),
                                      o -> o.ifPresent(uri -> vm.startExport(uri)));

    /** View Binding. */
    private FragmentExportBinding vb;
    @Nullable
    private ProgressDelegate progressDelegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(ExportViewModel.class);
        // no init
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentExportBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getToolbar().setTitle(R.string.title_backup_and_export);

        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        vm.onWriteDataCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        vm.onWriteDataFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        vm.onWriteDataFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        vb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .getExportHelper().setRecordType(isChecked, RecordType.Cover));
        vb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> vm
                .getExportHelper().setIncremental(checkedId == vb.rbExportNewAndUpdated.getId()));

        vb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vm.getExportHelper().setRecordType(isChecked, RecordType.Books);
            vb.rbBooksGroup.setEnabled(isChecked);
        });

        vb.archiveFormat.setOnItemClickListener(
                (p, v, position, id) -> updateFormatSelection(vm.getEncoding(position)));

        vb.infExportNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);

        vb.btnStart.setOnClickListener(v -> startExport());

        if (!vm.isRunning()) {
            // The task is NOT yet running.
            // Show either the full-options screen or the quick-options dialog
            if (vm.isQuickOptionsAlreadyShown()) {
                showOptions();
            } else {
                showQuickOptions();
            }
        }
    }

    private void showQuickOptions() {
        vm.setQuickOptionsAlreadyShown();

        final ExportHelper helper = vm.getExportHelper();
        // set the default; a backup to archive
        helper.setEncoding(ArchiveEncoding.Zip);

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.info_export_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                .setNeutralButton(R.string.action_show_options, (d, w) -> {
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

    /**
     * Show the full options screen to the user.
     */
    private void showOptions() {
        final ExportHelper helper = vm.getExportHelper();

        final Set<RecordType> recordTypes = helper.getRecordTypes();
        vb.cbxBooks.setChecked(recordTypes.contains(RecordType.Books));
        vb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));

        final boolean incremental = helper.isIncremental();
        vb.rbExportAll.setChecked(!incremental);
        vb.rbExportNewAndUpdated.setChecked(incremental);

        //noinspection ConstantConditions
        final Pair<Integer, ArrayList<String>> fo = vm.getFormatOptions(getContext());
        final int initialPos = fo.first;
        final ArrayList<String> list = fo.second;

        vb.archiveFormat.setAdapter(new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Passthrough, list));

        vb.archiveFormat.setText(list.get(initialPos), false);
        updateFormatSelection(helper.getEncoding());

        vb.getRoot().setVisibility(View.VISIBLE);
    }

    private void updateFormatSelection(@NonNull final ArchiveEncoding encoding) {

        final ExportHelper helper = vm.getExportHelper();
        helper.setEncoding(encoding);

        vb.archiveFormatInfo.setText(encoding.getShortDescResId());

        switch (encoding) {
            case Zip: {
                vb.archiveFormatInfoLong.setText("");

                // Don't change Books/Covers, but add:
                helper.addRecordType(EnumSet.of(RecordType.Styles,
                                                RecordType.Preferences,
                                                RecordType.Certificates));

                vb.cbxBooks.setChecked(true);
                vb.cbxBooks.setEnabled(true);

                vb.rbBooksGroup.setEnabled(true);
                vb.rbExportNewAndUpdated.setChecked(true);

                vb.cbxCovers.setChecked(true);
                vb.cbxCovers.setEnabled(true);
                break;
            }
            case Json: {
                vb.archiveFormatInfoLong.setText("");

                helper.removeRecordType(EnumSet.of(RecordType.Styles,
                                                   RecordType.Preferences,
                                                   RecordType.Certificates));

                vb.cbxBooks.setChecked(true);
                vb.cbxBooks.setEnabled(false);

                vb.rbBooksGroup.setEnabled(true);
                vb.rbExportAll.setChecked(true);

                vb.cbxCovers.setChecked(false);
                vb.cbxCovers.setEnabled(false);
                break;
            }
            case SqLiteDb: {
                vb.archiveFormatInfoLong.setText(R.string.option_info_lbl_archive_is_export_only);

                helper.removeRecordType(EnumSet.of(RecordType.Styles,
                                                   RecordType.Preferences,
                                                   RecordType.Certificates));

                vb.cbxBooks.setChecked(true);
                vb.cbxBooks.setEnabled(false);

                vb.rbBooksGroup.setEnabled(false);
                vb.rbExportAll.setChecked(true);

                vb.cbxCovers.setChecked(false);
                vb.cbxCovers.setEnabled(false);
                break;
            }
            case Csv:
            default:
                throw new IllegalArgumentException(encoding.toString());
        }
    }

    private void startExport() {
        if (vm.isReadyToGo()) {
            exportPickUri();
        } else {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_nothing_selected,
                          Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    /**
     * Prompt the user for a uri to export to.
     */
    private void exportPickUri() {
        // Create the proposed name for the archive. The user can change it.
        final String fileName = "ntmb-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        final String mimeType = FileUtils.getMimeTypeFromExtension(
                vm.getExportHelper().getEncoding().getFileExt());

        createDocumentLauncher.launch(new GetContentUriForWritingContract
                .Input(mimeType, fileName));
    }

    private void onExportCancelled(
            @NonNull final LiveDataEvent<TaskResult<ExportResults>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.DELAY_LONG_MS);
        });
    }

    private void onExportFailure(@NonNull final LiveDataEvent<TaskResult<Throwable>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, data.getResult())
                                    .orElse(getString(R.string.error_unknown));

            @StringRes
            final int title = vm.getExportHelper().isBackup()
                              ? R.string.error_backup_failed
                              : R.string.error_export_failed;

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(title)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        });
    }

    /**
     * Export finished/failed: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(@NonNull final LiveDataEvent<TaskResult<ExportResults>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::requireResult).ifPresent(result -> {
            final List<String> items = extractExportedItems(result);
            if (items.isEmpty()) {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.title_backup_and_export)
                        .setMessage(R.string.warning_export_contains_no_data)
                        .setPositiveButton(R.string.action_done, (d, w)
                                -> getActivity().finish())
                        .create()
                        .show();
            } else {

                final String itemList = items
                        .stream()
                        .map(s -> getString(R.string.list_element, s))
                        .collect(Collectors.joining("\n"));

                final ExportHelper helper = vm.getExportHelper();


                @StringRes
                final int title = helper.isBackup() ? R.string.info_backup_successful
                                                    : R.string.info_export_successful;

                //noinspection ConstantConditions
                final MaterialAlertDialogBuilder dialogBuilder =
                        new MaterialAlertDialogBuilder(getContext())
                                .setIcon(R.drawable.ic_baseline_info_24)
                                .setTitle(title)
                                .setPositiveButton(R.string.action_done, (d, w)
                                        -> getActivity().finish());

                final StringBuilder msg = new StringBuilder(itemList);

                final UriInfo uriInfo = new UriInfo(helper.getUri());
                final long size = uriInfo.getSize(getContext());

                // We cannot get the folder name for the file.
                // FIXME: We need to change the descriptive string not to include the folder.
                msg.append("\n\n")
                   .append(getString(R.string.info_export_report, "",
                                     uriInfo.getDisplayName(getContext()),
                                     FileUtils.formatFileSize(getContext(), size)));

                if (size > 0 && size < MAX_FILE_SIZE_FOR_EMAIL) {
                    msg.append("\n\n").append(getString(R.string.confirm_email_file));

                    dialogBuilder.setNeutralButton(R.string.action_email, (d, w) ->
                            onExportEmail(uriInfo.getUri(), itemList));
                }

                dialogBuilder.setMessage(msg)
                             .create()
                             .show();
            }
        });
    }

    @NonNull
    private List<String> extractExportedItems(@NonNull final ExportResults result) {
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
        if (result.bookshelves > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_bookshelves),
                                String.valueOf(result.bookshelves)));
        }
        if (result.calibreLibraries > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_library),
                                String.valueOf(result.calibreLibraries)));
        }
        if (result.styles > 0) {
            // deduct built-in styles
            final int nr = result.styles - BuiltinStyle.size();
            items.add(getString(R.string.name_colon_value, getString(R.string.lbl_styles),
                                String.valueOf(nr)));
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

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (progressDelegate == null) {
                //noinspection ConstantConditions
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.title_backup_and_export)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancelTask(data.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(data);
        });
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
                               + getString(R.string.info_backup_email_subject);

        try {
            final Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TEXT, report)
                    .putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(intent);
            //noinspection ConstantConditions
            getActivity().finish();

        } catch (@NonNull final ActivityNotFoundException e) {
            Logger.error(TAG, e);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setMessage(R.string.error_email_failed)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection ConstantConditions
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }
}
