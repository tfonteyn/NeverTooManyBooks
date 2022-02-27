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

import android.content.ActivityNotFoundException;
import android.content.Context;
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
import androidx.core.view.MenuProvider;
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
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class ExportFragment
        extends BaseFragment {

    /** Log tag. */
    private static final String TAG = "ExportFragment";

    /** The maximum file size for an export file for which we'll offer to send it as an email. */
    private static final int MAX_FILE_SIZE_FOR_EMAIL = 5_000_000;
    @NonNull
    private final MenuProvider mToolbarMenuProvider = new ToolbarMenuProvider();
    /** The ViewModel. */
    private ExportViewModel mVm;
    /** The launcher for picking a Uri to write to. */
    private final ActivityResultLauncher<String> mCreateDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(),
                                      this::exportToUri);

    /** View Binding. */
    private FragmentExportBinding mVb;
    @Nullable
    private ProgressDelegate mProgressDelegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(ExportViewModel.class);
        // no init
    }

    @Override
    @Nullable
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

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(R.string.menu_backup_and_export);

        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onWriteDataCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        mVm.onWriteDataFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        mVm.onWriteDataFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> mVm
                .getExportHelper().setRecordType(isChecked, RecordType.Cover));
        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> mVm
                .getExportHelper().setIncremental(checkedId == mVb.rbExportNewAndUpdated.getId()));

        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mVm.getExportHelper().setRecordType(isChecked, RecordType.Books);
            mVb.rbBooksGroup.setEnabled(isChecked);
        });

        mVb.archiveFormat.setOnItemClickListener(
                (p, v, position, id) -> updateFormatSelection(mVm.getEncoding(position)));

        mVb.infExportNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);

        if (!mVm.isRunning()) {
            // The task is NOT yet running.
            // Show either the full-options screen or the quick-options dialog
            if (mVm.isQuickOptionsAlreadyShown()) {
                showOptions();
            } else {
                showQuickOptions();
            }
        }
    }

    private void showQuickOptions() {
        mVm.setQuickOptionsAlreadyShown();

        final ExportHelper helper = mVm.getExportHelper();
        // set the default; a backup to archive
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

    /**
     * Export Step 1b: Show the full options screen to the user.
     */
    private void showOptions() {
        final ExportHelper helper = mVm.getExportHelper();

        final Set<RecordType> recordTypes = helper.getRecordTypes();
        mVb.cbxBooks.setChecked(recordTypes.contains(RecordType.Books));
        mVb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));

        final boolean incremental = helper.isIncremental();
        mVb.rbExportAll.setChecked(!incremental);
        mVb.rbExportNewAndUpdated.setChecked(incremental);

        //noinspection ConstantConditions
        final Pair<Integer, ArrayList<String>> fo = mVm.getFormatOptions(getContext());
        final int initialPos = fo.first;
        final ArrayList<String> list = fo.second;

        mVb.archiveFormat.setAdapter(new ExtArrayAdapter<>(
                getContext(), R.layout.popup_dropdown_menu_item,
                ExtArrayAdapter.FilterType.Passthrough, list));

        mVb.archiveFormat.setText(list.get(initialPos), false);
        updateFormatSelection(helper.getEncoding());

        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void updateFormatSelection(@NonNull final ArchiveEncoding encoding) {

        final ExportHelper helper = mVm.getExportHelper();
        helper.setEncoding(encoding);

        mVb.archiveFormatInfo.setText(encoding.getShortDescResId());

        switch (encoding) {
            case Zip: {
                mVb.archiveFormatInfoLong.setText("");

                // Don't change Books/Covers, but add:
                helper.addRecordType(EnumSet.of(RecordType.Styles,
                                                RecordType.Preferences,
                                                RecordType.Certificates));

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(true);

                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbExportNewAndUpdated.setChecked(true);

                mVb.cbxCovers.setChecked(true);
                mVb.cbxCovers.setEnabled(true);
                break;
            }
            case Csv: {
                mVb.archiveFormatInfoLong.setText("");

                helper.removeRecordType(EnumSet.of(RecordType.Styles,
                                                   RecordType.Preferences,
                                                   RecordType.Certificates));

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);

                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbExportNewAndUpdated.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);
                break;
            }
            case Json: {
                mVb.archiveFormatInfoLong.setText("");

                helper.removeRecordType(EnumSet.of(RecordType.Styles,
                                                   RecordType.Preferences,
                                                   RecordType.Certificates));

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);

                mVb.rbBooksGroup.setEnabled(true);
                mVb.rbExportAll.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);
                break;
            }
            case Xml:
            case SqLiteDb: {
                mVb.archiveFormatInfoLong.setText(R.string.lbl_archive_is_export_only);

                helper.removeRecordType(EnumSet.of(RecordType.Styles,
                                                   RecordType.Preferences,
                                                   RecordType.Certificates));

                mVb.cbxBooks.setChecked(true);
                mVb.cbxBooks.setEnabled(false);

                // See class docs for XmlArchiveWriter
                mVb.rbBooksGroup.setEnabled(false);
                mVb.rbExportAll.setChecked(true);

                mVb.cbxCovers.setChecked(false);
                mVb.cbxCovers.setEnabled(false);
                break;
            }
            case Tar:
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
            mVm.startExport(uri);
        }
    }

    private void onExportCancelled(
            @NonNull final LiveDataEvent<TaskResult<ExportResults>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
        });
    }

    private void onExportFailure(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, data.getResult())
                                    .orElse(getString(R.string.error_unknown));

            @StringRes
            final int title = mVm.getExportHelper().isBackup()
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

                final UriInfo uriInfo = new UriInfo(helper.getUri());
                final long size = uriInfo.getSize(getContext());

                // We cannot get the folder name for the file.
                // FIXME: We need to change the descriptive string not to include the folder.
                msg.append("\n\n")
                   .append(getString(R.string.progress_end_export_report, "",
                                     uriInfo.getDisplayName(getContext()),
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
//        if (result.calibreLibraries > 0) {
//            items.add(getString(R.string.name_colon_value,
//                                getString(R.string.lbl_library),
//                                String.valueOf(result.calibreLibraries)));
//        }
        if (result.styles > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_styles),
                                // deduct built-in styles (remember: MAX_ID is negative)
                                String.valueOf(result.styles + BuiltinStyle.MAX_ID)));
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
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.menu_backup_and_export)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> mVm.cancelTask(data.taskId))
                        .show(getActivity().getWindow());
            }
            mProgressDelegate.onProgress(data);
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
        if (mProgressDelegate != null) {
            //noinspection ConstantConditions
            mProgressDelegate.dismiss(getActivity().getWindow());
            mProgressDelegate = null;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.toolbar_action_go, menu);

            final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
            final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
            button.setText(menuItem.getTitle());
            button.setOnClickListener(v -> onMenuItemSelected(menuItem));

            onPrepareMenu(menu);
        }

        public void onPrepareMenu(@NonNull final Menu menu) {
            menu.findItem(R.id.MENU_ACTION_CONFIRM)
                .setEnabled(mVm.isReadyToGo());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM) {
                exportPickUri();
                return true;
            }
            return false;
        }
    }

}
