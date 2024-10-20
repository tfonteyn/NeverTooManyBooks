/*
 * @Copyright 2018-2024 HardBackNutter
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.GetContentUriForReadingContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.backup.csv.CsvRecordReader;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.widgets.insets.InsetsListenerBuilder;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateUtils;

public class ImportFragment
        extends BaseFragment {

    /** Fragment/Log tag. */
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
        InsetsListenerBuilder.fragmentRootView(view);

        //noinspection DataFlowIssue
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        final Toolbar toolbar = getToolbar();
        toolbar.setTitle(R.string.lbl_import);

        vm.onReadMetaDataFinished().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        vm.onReadMetaDataCancelled().observe(getViewLifecycleOwner(), this::onMetaDataCancelled);
        vm.onReadMetaDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        vm.onReadDataFinished().observe(getViewLifecycleOwner(), this::onImportFinished);
        vm.onReadDataCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        vm.onReadDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);

        // Hookup the [I] information icons with their popup text
        vb.infNewOnly.setOnClickListener(StandardDialogs::infoPopup);
        vb.infNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);
        vb.infAll.setOnClickListener(StandardDialogs::infoPopup);
        vb.infRemovedBooks.setOnClickListener(StandardDialogs::infoPopup);

        vb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vm.setRecordType(isChecked, RecordType.Books);

            vb.rbImportNewOnly.setEnabled(isChecked);
            vb.rbImportAll.setEnabled(isChecked);
            vb.rbImportNewAndUpdated.setEnabled(isChecked);

            updateUpdateOptionRadioButtons();

            // follow the cbxBooks status
            vb.cbxDeleteRemovedBooks.setEnabled(isChecked);
        });

        vb.cbxDeleteRemovedBooks.setOnCheckedChangeListener(
                (buttonView, isChecked) -> vm.setRemoveDeletedBooksAfterImport(isChecked));

        vb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .setRecordType(isChecked, RecordType.Cover));

        vb.cbxStyles.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .setRecordType(isChecked, RecordType.Styles));

        vb.cbxPrefs.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .setRecordType(isChecked, RecordType.Preferences, RecordType.Certificates));

        vb.rbgBooks.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == vb.rbImportNewOnly.getId()) {
                vm.setUpdateOption(DataReader.Updates.Skip);
            } else if (checkedId == vb.rbImportNewAndUpdated.getId()) {
                vm.setUpdateOption(DataReader.Updates.OnlyNewer);
            } else if (checkedId == vb.rbImportAll.getId()) {
                vm.setUpdateOption(DataReader.Updates.Overwrite);
            }
        });

        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.download_24px);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> onStartImport());

        if (!vm.isRunning()) {
            if (vm.hasSource()) {
                // e.g. after a screen rotation
                updateUI();
            } else {
                // start the import process by asking the user for a source Uri
                openUriLauncher.launch(MIME_TYPES);
            }
        }
    }

    /**
     * Called when the user selected a uri to read from.
     * When the uri is supported, the screen will be show and meta-data will be read.
     *
     * @param uri file to read from
     */
    private void onOpenUri(@NonNull final Uri uri) {

        try {
            //noinspection DataFlowIssue
            vm.setSource(getContext(), uri,
                         ServiceLocator.getInstance().getSystemLocaleList().get(0));

            // FIRST show the screen for better user feedback
            updateUI();

            // There will be no progress messages as reading the data itself is very fast, but
            // connection can take a long time, so bring up the progress dialog now
            closeProgressDialog();
            //noinspection DataFlowIssue
            progressDelegate = new ProgressDelegate(getProgressFrame())
                    .setTitle(R.string.lbl_importing)
                    .setMessage(R.string.progress_msg_please_wait)
                    .setPreventSleep(true)
                    .setIndeterminate(true)
                    .setOnCancelListener(v -> vm.cancelTask(R.id.TASK_ID_READ_META_DATA))
                    .show(() -> getActivity().getWindow());
            // The screen will refresh after meta-data has been read
            vm.readMetaData();

        } catch (@NonNull final DataReaderException e) {
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.error_24px)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(e.getUserMessage(getContext()))
                    .setPositiveButton(R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        } catch (@NonNull final FileNotFoundException e) {
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.error_24px)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(getString(R.string.error_file_not_found, uri.getPath()))
                    .setPositiveButton(R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    private void onMetaDataRead(@NonNull final LiveDataEvent<Optional<ArchiveMetaData>> message) {
        closeProgressDialog();
        message.process(optMetaData -> optMetaData.ifPresent(md -> updateUI()));
    }

    private void onMetaDataCancelled(@NonNull final LiveDataEvent<Optional<ArchiveMetaData>>
                                             message) {
        closeProgressDialog();
        message.process(ignored -> showMessageAndFinishActivity(getString(R.string.cancelled)));
    }

    private void updateUI() {

        // Set the visibility depending on the encoding
        switch (vm.getEncoding()) {
            case Zip: {
                // all options available
                vb.cbxBooks.setVisibility(View.VISIBLE);
                vb.cbxCovers.setVisibility(View.VISIBLE);
                vb.cbxPrefs.setVisibility(View.VISIBLE);
                vb.cbxStyles.setVisibility(View.VISIBLE);
                vb.cbxDeleteRemovedBooks.setVisibility(View.VISIBLE);
                break;
            }
            case Json: {
                // all options, except covers
                vb.cbxBooks.setVisibility(View.VISIBLE);
                vb.cbxCovers.setVisibility(View.GONE);
                vb.cbxPrefs.setVisibility(View.VISIBLE);
                vb.cbxStyles.setVisibility(View.VISIBLE);
                vb.cbxDeleteRemovedBooks.setVisibility(View.VISIBLE);
                break;
            }
            case Csv:
            case SqLiteDb: {
                // Show only the book options;
                // The following are not applicable
                vb.cbxBooks.setVisibility(View.GONE);
                vb.cbxCovers.setVisibility(View.GONE);
                vb.cbxPrefs.setVisibility(View.GONE);
                vb.cbxStyles.setVisibility(View.GONE);
                vb.cbxDeleteRemovedBooks.setVisibility(View.GONE);
                break;
            }
        }

        final Set<RecordType> recordTypes = vm.getRecordTypes();
        vb.cbxBooks.setChecked(recordTypes.contains(RecordType.Books));
        vb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));
        vb.cbxStyles.setChecked(recordTypes.contains(RecordType.Styles));
        vb.cbxPrefs.setChecked(recordTypes.contains(RecordType.Preferences));

        final DataReader.Updates updateOption = vm.getUpdateOption();
        vb.rbImportNewOnly.setChecked(updateOption == DataReader.Updates.Skip);
        vb.rbImportNewAndUpdated.setChecked(updateOption == DataReader.Updates.OnlyNewer);
        vb.rbImportAll.setChecked(updateOption == DataReader.Updates.Overwrite);

        vb.cbxDeleteRemovedBooks.setChecked(vm.isRemoveDeletedBooksAfterImport());
        vb.infRemovedBooks.setVisibility(vb.cbxDeleteRemovedBooks.getVisibility());

        //noinspection DataFlowIssue
        vb.archiveName.setText(vm.getSourceDisplayName(getContext()));

        vm.getMetaData().ifPresentOrElse(
                metaData -> {
                    final StringJoiner info = new StringJoiner("\n");
                    final Context context = getContext();

                    @Nullable
                    final CsvRecordReader.Origin origin = metaData.getData().getParcelable(
                            CsvRecordReader.Origin.BKEY);
                    if (origin != null) {
                        info.add(context.getString(R.string.name_colon_value,
                                                   context.getString(R.string.lbl_archive_format),
                                                   origin.getLabel(context)));
                    }

                    final Locale systemLocale = ServiceLocator
                            .getInstance().getSystemLocaleList().get(0);
                    metaData.getCreatedLocalDate(systemLocale).ifPresent(date -> info
                            .add(DateUtils.displayDateTime(context, date)));

                    metaData.getBookCount().ifPresent(count -> info
                            .add(getString(R.string.name_colon_value,
                                           getString(R.string.lbl_books),
                                           String.valueOf(count))));

                    metaData.getCoverCount().ifPresent(count -> info
                            .add(getString(R.string.name_colon_value,
                                           getString(R.string.lbl_covers),
                                           String.valueOf(count))));

                    vb.archiveContent.setText(info.toString());
                    vb.archiveContent.setVisibility(View.VISIBLE);

                    updateUpdateOptionRadioButtons();
                },
                // Hide all meta-data
                () -> vb.archiveContent.setVisibility(View.INVISIBLE)
        );

        vb.getRoot().setVisibility(View.VISIBLE);
    }

    private void updateUpdateOptionRadioButtons() {
        final boolean supportsUpdates = vm.sourceSupportsUpdates();
        vb.rbImportNewAndUpdated.setEnabled(supportsUpdates);
        if (!supportsUpdates) {
            vb.rbImportNewOnly.setChecked(true);
        }
    }

    /**
     * Conditionally start the import.
     */
    private void onStartImport() {
        if (!vm.isReadyToGo()) {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.warning_nothing_selected, Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }

        if (vm.getEncoding() == ArchiveEncoding.Csv) {
            // CsvArchiveReader will make a database backup before importing.
            //noinspection DataFlowIssue
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.warning_24px)
                    .setTitle(R.string.lbl_import_books)
                    .setMessage(R.string.warning_import_csv)
                    .setNegativeButton(R.string.cancel,
                                       (d, w) -> getActivity().finish())
                    .setPositiveButton(R.string.ok, (d, w) -> startImport())
                    .create()
                    .show();
        } else {
            startImport();
        }
    }

    private void startImport() {
        closeProgressDialog();
        //noinspection DataFlowIssue
        progressDelegate = new ProgressDelegate(getProgressFrame())
                .setTitle(R.string.lbl_importing)
                .setPreventSleep(true)
                .setOnCancelListener(v -> vm.cancelTask(R.id.TASK_ID_IMPORT))
                .show(() -> getActivity().getWindow());
        vm.readData();
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.process(progress -> {
            closeProgressDialog();
            //noinspection DataFlowIssue
            progressDelegate = new ProgressDelegate(getProgressFrame())
                    .setTitle(R.string.lbl_importing)
                    .setPreventSleep(true)
                    .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                    .show(() -> getActivity().getWindow());

            progressDelegate.onProgress(progress);
        });
    }

    private void onImportFailure(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e, getString(R.string.error_import_failed),
                             (d, w) -> getActivity().finish());
        });
    }

    private void onImportCancelled(@NonNull final LiveDataEvent<ImportResults> message) {
        closeProgressDialog();

        message.process(importResults -> {
            if (importResults != null) {
                onImportFinished(R.string.info_import_partially_complete, importResults);
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
    private void onImportFinished(@NonNull final LiveDataEvent<ImportResults> message) {
        closeProgressDialog();

        message.process(importResults ->
                                onImportFinished(R.string.info_import_complete, importResults));
    }

    /**
     * Import finished/cancelled: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports full or partial import.
     * @param result  of the import
     */
    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ImportResults result) {

        //noinspection DataFlowIssue
        vm.postProcessStyles(getContext(), result);
        result.booksDeleted = vm.postProcessDeletedBooks();

        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.info_24px)
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
        //noinspection DataFlowIssue
        final String reportSuccess = String.join("\n", result.createReport(getContext()));

        if (result.booksFailed == 0) {
            return reportSuccess;
        }

        final List<String> failures = result.createFailuresReport(getContext());
        String reportFailure = '\n' + getString(R.string.warning_import_failed_for_these_books)
                               + '\n' + String.join("\n", failures);

        if (result.booksFailed > ImportResults.MAX_FAIL_LINES_REPORTED) {
            reportFailure += '\n' + getString(
                    R.string.warning_import_failed_for_these_books_and_more,
                    result.booksFailed - failures.size());
        }
        return reportSuccess + "\n" + reportFailure;
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }
}
