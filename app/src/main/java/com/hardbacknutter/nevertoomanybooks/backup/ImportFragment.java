/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateUtils;

public class ImportFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "ImportFragment";

    /**
     * The mime types accepted for importing files SHOULD be set to this list:
     * "application/zip", "application/x-tar", "text/csv", "application/x-sqlite3"
     * <p>
     * As it turns out the mime types are not an Android feature, but support for them
     * is specific to whatever application is responding to {@link Intent#ACTION_GET_CONTENT}
     * which in practice is extremely limited.
     * e.g. "text/*" does not even allow csv files in the standard Android 8.0 Files app.
     * <p>
     * So we have no choice but to accept simply all files and deal with invalid ones later.
     * Also see {@link #openUriLauncher}.
     */
    private static final String MIME_TYPES = "*/*";

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    // no result as we didn't do anything
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK);
                    getActivity().finish();
                }
            };
    /** The ViewModel. */
    private ImportViewModel vm;

    /** View Binding. */
    private FragmentImportBinding vb;
    @Nullable
    private ProgressDelegate progressDelegate;

    /** The launcher for picking a Uri to read from. */
    private final ActivityResultLauncher<String> openUriLauncher =
            registerForActivityResult(new GetContentUriForReadingContract(), o -> {
                if (o.isPresent()) {
                    onOpenUri(o.get());
                } else {
                    // nothing selected, just quit
                    //noinspection DataFlowIssue
                    getActivity().finish();
                }
            });

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(ImportViewModel.class);
        // no init
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentImportBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getToolbar().setTitle(R.string.lbl_import);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        vm.onReadMetaDataFinished().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        vm.onReadMetaDataCancelled().observe(getViewLifecycleOwner(), this::onMetaDataCancelled);
        vm.onReadMetaDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        vm.onReadDataFinished().observe(getViewLifecycleOwner(), this::onImportFinished);
        vm.onReadDataCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        vm.onReadDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);

        vb.infNewOnly.setOnClickListener(StandardDialogs::infoPopup);
        vb.infNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);
        vb.infAll.setOnClickListener(StandardDialogs::infoPopup);

        vb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vm.getDataReaderHelper().setRecordType(isChecked, RecordType.Books);
            vb.rbBooksGroup.setEnabled(isChecked);
        });

        vb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .getDataReaderHelper().setRecordType(isChecked, RecordType.Cover));

        vb.cbxStyles.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .getDataReaderHelper().setRecordType(isChecked, RecordType.Styles));

        vb.cbxPrefs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            final ImportHelper helper = vm.getDataReaderHelper();
            helper.setRecordType(isChecked, RecordType.Preferences);
            helper.setRecordType(isChecked, RecordType.Certificates);
        });

        vb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            final ImportHelper helper = vm.getDataReaderHelper();
            if (checkedId == vb.rbImportNewOnly.getId()) {
                helper.setUpdateOption(DataReader.Updates.Skip);
            } else if (checkedId == vb.rbImportNewAndUpdated.getId()) {
                helper.setUpdateOption(DataReader.Updates.OnlyNewer);
            } else if (checkedId == vb.rbImportAll.getId()) {
                helper.setUpdateOption(DataReader.Updates.Overwrite);
            }
        });

        final ExtendedFloatingActionButton fab = getFab();
        fab.setIconResource(R.drawable.ic_baseline_import);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> startImport());

        if (!vm.isRunning()) {
            if (vm.hasUri()) {
                // if we already have a uri when called (from getArguments()),
                // or e.g. after a screen rotation, just show the screen/options again
                showOptions();
            } else {
                // start the import process by asking the user for a Uri
                openUriLauncher.launch(MIME_TYPES);
            }
        }
    }


    /**
     * Called when the user selected a uri to read from.
     * Prepares the options suited for the selected import file.
     *
     * @param uri file to read from
     */
    private void onOpenUri(@NonNull final Uri uri) {
        final ImportHelper importHelper;
        try {
            //noinspection DataFlowIssue
            importHelper = vm.createDataReaderHelper(getContext(), uri,
                                                     ServiceLocator.getInstance()
                                                                   .getSystemLocaleList().get(0));

        } catch (@NonNull final DataReaderException e) {
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(e.getUserMessage(getContext()))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
            return;

        } catch (@NonNull final FileNotFoundException e) {
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(getString(R.string.error_file_not_found, uri.getPath()))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
            return;
        }

        switch (importHelper.getEncoding()) {
            case Csv:
                // CsvArchiveReader will make a database backup before importing.
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.lbl_import_books)
                        .setMessage(R.string.warning_import_csv)
                        .setNegativeButton(android.R.string.cancel,
                                           (d, w) -> getActivity().finish())
                        .setPositiveButton(android.R.string.ok, (d, w) -> showOptions())
                        .create()
                        .show();
                break;

            case Zip:
            case SqLiteDb:
            case Json:
                showOptions();
                break;

            default:
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_error_24)
                        .setTitle(R.string.error_import_failed)
                        .setMessage(R.string.error_import_file_not_supported)
                        .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                        .create()
                        .show();
                break;
        }
    }

    /**
     * Update the screen with archive specific options and values.
     */
    private void showOptions() {
        final ImportHelper helper = vm.getDataReaderHelper();

        //noinspection DataFlowIssue
        vb.archiveName.setText(helper.getUriInfo().getDisplayName(getContext()));

        final Optional<ArchiveMetaData> metaData = helper.getMetaData();
        if (metaData.isPresent()) {
            showMetaData(metaData.get());
        } else {
            readMetaData();
            showMetaData(null);
        }

        final Set<RecordType> recordTypes = helper.getRecordTypes();
        vb.cbxBooks.setChecked(recordTypes.contains(RecordType.Books));
        vb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));
        vb.cbxStyles.setChecked(recordTypes.contains(RecordType.Styles));
        vb.cbxPrefs.setChecked(recordTypes.contains(RecordType.Preferences));

        final DataReader.Updates updateOption = helper.getUpdateOption();
        vb.rbImportNewOnly.setChecked(updateOption == DataReader.Updates.Skip);
        vb.rbImportNewAndUpdated.setChecked(updateOption == DataReader.Updates.OnlyNewer);
        vb.rbImportAll.setChecked(updateOption == DataReader.Updates.Overwrite);

        // Set the visibility depending on the encoding
        switch (helper.getEncoding()) {
            case Zip: {
                // all options available
                vb.cbxBooks.setVisibility(View.VISIBLE);
                vb.cbxCovers.setVisibility(View.VISIBLE);
                vb.cbxPrefs.setVisibility(View.VISIBLE);
                vb.cbxStyles.setVisibility(View.VISIBLE);
                break;
            }
            case Json: {
                // all options, except covers
                vb.cbxBooks.setVisibility(View.VISIBLE);
                vb.cbxCovers.setVisibility(View.GONE);
                vb.cbxPrefs.setVisibility(View.VISIBLE);
                vb.cbxStyles.setVisibility(View.VISIBLE);
                break;
            }
            case Csv:
            case SqLiteDb: {
                // show only the book options
                vb.cbxBooks.setVisibility(View.GONE);
                vb.cbxCovers.setVisibility(View.GONE);
                vb.cbxPrefs.setVisibility(View.GONE);
                vb.cbxStyles.setVisibility(View.GONE);
                break;
            }
        }

        vb.getRoot().setVisibility(View.VISIBLE);
    }

    private void readMetaData() {
        // There will be no progress messages as reading the data itself is very fast, but
        // connection can take a long time, so bring up the progress dialog now
        if (progressDelegate == null) {
            progressDelegate = new ProgressDelegate(getProgressFrame())
                    .setTitle(R.string.progress_msg_connecting)
                    .setPreventSleep(true)
                    .setIndeterminate(true)
                    .setOnCancelListener(v -> vm.cancelTask(R.id.TASK_ID_READ_META_DATA));
        }
        //noinspection DataFlowIssue
        progressDelegate.show(() -> getActivity().getWindow());
        vm.readMetaData();
    }

    private void onMetaDataRead(@NonNull final LiveDataEvent<TaskResult<
            Optional<ArchiveMetaData>>> message) {
        closeProgressDialog();

        message.getData().flatMap(TaskResult::requireResult).ifPresent(this::showMetaData);
    }

    private void onMetaDataCancelled(@NonNull final LiveDataEvent<TaskResult<
            Optional<ArchiveMetaData>>> message) {
        closeProgressDialog();

        message.getData().ifPresent(
                data -> showMessageAndFinishActivity(getString(R.string.cancelled)));
    }

    /**
     * Display any valid meta-data we can get from the archive.
     *
     * @param metaData as read from the import source (archive)
     */
    private void showMetaData(@Nullable final ArchiveMetaData metaData) {
        if (metaData == null) {
            vb.archiveContent.setVisibility(View.INVISIBLE);
        } else {
            // some stats of what's inside the archive
            final StringJoiner stats = new StringJoiner("\n");

            final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
            //noinspection DataFlowIssue
            metaData.getCreatedLocalDate(systemLocale).ifPresent(date -> stats
                    .add(DateUtils.displayDateTime(getContext(), date)));

            metaData.getBookCount().ifPresent(count -> stats
                    .add(getString(R.string.name_colon_value,
                                   getString(R.string.lbl_books),
                                   String.valueOf(count))));

            metaData.getCoverCount().ifPresent(count -> stats
                    .add(getString(R.string.name_colon_value,
                                   getString(R.string.lbl_covers),
                                   String.valueOf(count))));

            vb.archiveContent.setText(stats.toString());
            vb.archiveContent.setVisibility(View.VISIBLE);
        }
    }

    private void startImport() {
        if (vm.isReadyToGo()) {
            if (progressDelegate == null) {
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.lbl_importing)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancelTask(R.id.TASK_ID_IMPORT));
            }
            //noinspection DataFlowIssue
            progressDelegate.show(() -> getActivity().getWindow());
            vm.readData();
        } else {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.warning_nothing_selected, Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (progressDelegate == null) {
                //noinspection DataFlowIssue
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.lbl_importing)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancelTask(data.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(data);
        });
    }

    private void onImportFailure(@NonNull final LiveDataEvent<TaskResult<Throwable>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::getResult).filter(Objects::nonNull).ifPresent(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), e, getString(R.string.error_import_failed),
                             (d, w) -> getActivity().finish());
        });
    }

    private void onImportCancelled(@NonNull final LiveDataEvent<TaskResult<
            ImportResults>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final ImportResults result = data.getResult();

            if (result != null) {
                onImportFinished(R.string.info_import_partially_complete, result);
            } else {
                showMessageAndFinishActivity(getString(R.string.cancelled));
            }
        });
    }

    /**
     * Import finished: Step 1: Process the message.
     *
     * @param message to process
     */
    private void onImportFinished(@NonNull final LiveDataEvent<TaskResult<
            ImportResults>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::requireResult).ifPresent(
                result -> onImportFinished(R.string.info_import_complete, result));
    }

    /**
     * Import finished/cancelled: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports full or partial import.
     * @param result  of the import
     */
    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ImportResults result) {

        if (result.styles > 0) {
            ServiceLocator.getInstance().getStyles().updateMenuOrder();
        }

        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(titleId)
                .setMessage(createReport(result))
                .setPositiveButton(R.string.action_done, (d, w) -> {
                    final Intent resultIntent = ImportContract.createResult(result);
                    //noinspection DataFlowIssue
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                })
                .create()
                .show();
    }

    /**
     * Transform the successful result data into a user friendly report.
     *
     * @param result to report
     *
     * @return report string
     */
    @NonNull
    private String createReport(@NonNull final ImportResults result) {

        final List<String> items = new ArrayList<>();

        if (result.booksCreated > 0 || result.booksUpdated > 0 || result.booksSkipped > 0) {
            items.add(getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                getString(R.string.lbl_books),
                                result.booksCreated,
                                result.booksUpdated,
                                result.booksSkipped));
        }
        if (result.coversCreated > 0 || result.coversUpdated > 0 || result.coversSkipped > 0) {
            items.add(getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                getString(R.string.lbl_covers),
                                result.coversCreated,
                                result.coversUpdated,
                                result.coversSkipped));
        }

        if (result.styles > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_styles),
                                // deduct built-in styles
                                String.valueOf(result.styles - BuiltinStyle.size())));
        }
        if (result.preferences > 0) {
            items.add(getString(R.string.lbl_settings));
        }
        if (result.certificates > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_certificates),
                                String.valueOf(result.certificates)));
        }

        final String report = items.stream()
                                   .map(s -> getString(R.string.list_element, s))
                                   .collect(Collectors.joining("\n"));

        int failed = result.failedLinesNr.size();
        if (failed == 0) {
            return report;
        }

        @StringRes
        final int fs;
        if (failed > ImportResults.MAX_FAIL_LINES) {
            failed = ImportResults.MAX_FAIL_LINES;
            fs = R.string.warning_import_failed_for_lines_lots;
        } else {
            fs = R.string.warning_import_failed_for_lines_some;
        }

        final Collection<String> itemList = new ArrayList<>();
        for (int i = 0; i < failed; i++) {
            itemList.add(getString(R.string.a_bracket_b_bracket,
                                   String.valueOf(result.failedLinesNr.get(i)),
                                   result.failedLinesMessage.get(i)));
        }

        return report + "\n" + getString(fs, itemList
                .stream()
                .map(s -> getString(R.string.list_element, s))
                .collect(Collectors.joining("\n")));
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }
}
