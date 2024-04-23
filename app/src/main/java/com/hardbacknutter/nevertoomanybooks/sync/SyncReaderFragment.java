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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.core.parsers.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.core.widgets.datepicker.SingleDatePicker;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncImportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateUtils;

public class SyncReaderFragment
        extends BaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SyncReaderFragment";
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection DataFlowIssue
                    getActivity().finish();
                }
            };
    /** The ViewModel. */
    private SyncReaderViewModel vm;

    /** View Binding. */
    private FragmentSyncImportBinding vb;

    private SingleDatePicker syncDatePicker;

    @Nullable
    private ProgressDelegate progressDelegate;

    /**
     * Constructor.
     *
     * @param syncServer to use
     *
     * @return instance
     */
    @NonNull
    public static Fragment create(@NonNull final SyncServer syncServer) {
        final Fragment fragment = new SyncReaderFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(SyncServer.BKEY_SITE, syncServer);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SyncReaderViewModel.class);
        vm.init(requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentSyncImportBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getToolbar().setTitle(vm.getSourceDisplayName(getContext()));

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

        vb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            vm.setRecordType(isChecked, RecordType.Books);
            vb.rbImportNewOnly.setEnabled(isChecked);
            vb.rbImportAll.setEnabled(isChecked);
            vb.rbImportNewAndUpdated.setEnabled(isChecked);
        });
        vb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .setRecordType(isChecked, RecordType.Cover));

        vb.infImportNewOnly.setOnClickListener(StandardDialogs::infoPopup);
        vb.infImportNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);
        vb.infImportAll.setOnClickListener(StandardDialogs::infoPopup);

        vb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == vb.rbImportNewOnly.getId()) {
                vm.setUpdateOption(DataReader.Updates.Skip);
            } else if (checkedId == vb.rbImportNewAndUpdated.getId()) {
                vm.setUpdateOption(DataReader.Updates.OnlyNewer);
            } else if (checkedId == vb.rbImportAll.getId()) {
                vm.setUpdateOption(DataReader.Updates.Overwrite);
            }

            updateSyncDateVisibility();
        });

        syncDatePicker = new SingleDatePicker(getChildFragmentManager(),
                                              R.string.lbl_sync_date,
                                              vb.lblSyncDate.getId());

        vb.syncDate.setOnClickListener(v -> syncDatePicker.launch(
                vm.getSyncDate(), this::onSyncDateSet));

        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.ic_baseline_import);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> startReading());

        if (!vm.isRunning()) {
            showOptions();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        syncDatePicker.onResume(this::onSyncDateSet);
    }

    /**
     * Update the screen with specific options and values.
     */
    private void showOptions() {

        final Optional<SyncReaderMetaData> metaData = vm.getMetaData();
        if (metaData.isPresent()) {
            showMetaData(metaData.get());
        } else {
            readMetaData();
            showMetaData(null);
        }

        final Set<RecordType> recordTypes = vm.getRecordTypes();
        vb.cbxBooks.setEnabled(false);
        vb.cbxBooks.setChecked(recordTypes.contains(RecordType.Books));
        vb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));

        final DataReader.Updates updateOption = vm.getUpdateOption();
        vb.rbImportNewOnly.setChecked(updateOption == DataReader.Updates.Skip);
        vb.rbImportAll.setChecked(updateOption == DataReader.Updates.Overwrite);
        vb.rbImportNewAndUpdated.setChecked(updateOption == DataReader.Updates.OnlyNewer);

        final boolean hasLastUpdateDateField = vm.getSyncServer().hasLastUpdateDateField();
        vb.rbImportNewAndUpdated.setEnabled(hasLastUpdateDateField);
        vb.infImportNewAndUpdated.setEnabled(hasLastUpdateDateField);

        updateSyncDateVisibility();

        vb.getRoot().setVisibility(View.VISIBLE);
    }

    private void startReading() {
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

    private void onMetaDataRead(@NonNull final LiveDataEvent<Optional<SyncReaderMetaData>> message) {
        closeProgressDialog();

        message.process(optMetaData -> optMetaData.ifPresent(this::showMetaData));
    }

    @SuppressWarnings("WeakerAccess")
    protected void onMetaDataCancelled(@NonNull final LiveDataEvent<Optional<SyncReaderMetaData>>
                                               message) {
        closeProgressDialog();

        message.process(ignored -> showMessageAndFinishActivity(getString(R.string.cancelled)));
    }

    /**
     * Display the name of the server + any valid data we can get from it.
     *
     * @param metaData as read from the import source (sync server)
     */
    private void showMetaData(@Nullable final SyncReaderMetaData metaData) {
        if (metaData == null) {
            vb.archiveContent.setVisibility(View.INVISIBLE);
            vb.lblCalibreLibrary.setVisibility(View.GONE);
        } else {
            switch (vm.getSyncServer()) {
                case CalibreCS: {
                    vb.archiveContent.setVisibility(View.VISIBLE);
                    vb.lblCalibreLibrary.setVisibility(View.VISIBLE);
                    showCalibreMetaData(metaData.getData());
                    break;
                }
                case StripInfo:
                default: {
                    vb.archiveContent.setVisibility(View.INVISIBLE);
                    vb.lblCalibreLibrary.setVisibility(View.GONE);
                    break;
                }
            }
        }
    }

    private void showCalibreMetaData(@NonNull final Bundle data) {

        final List<CalibreLibrary> libraries =
                data.getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST);

        //noinspection DataFlowIssue
        if (libraries.size() == 1) {
            // shortcut...
            onCalibreLibrarySelected(libraries.get(0));

        } else {
            //noinspection DataFlowIssue
            final ExtArrayAdapter<CalibreLibrary> adapter =
                    new EntityArrayAdapter<>(getContext(), libraries);

            vb.calibreLibrary.setAdapter(adapter);
            vb.calibreLibrary.setOnItemClickListener(
                    (av, v, position, id) -> onCalibreLibrarySelected(libraries.get(position)));

            CalibreLibrary library = vm.getExtraArgs()
                                       .getParcelable(CalibreContentServer.BKEY_LIBRARY);
            if (library == null) {
                library = data.getParcelable(CalibreContentServer.BKEY_LIBRARY);
            }
            //noinspection DataFlowIssue
            onCalibreLibrarySelected(library);
        }
    }

    private void onCalibreLibrarySelected(@NonNull final CalibreLibrary library) {
        vb.calibreLibrary.setText(library.getName(), false);

        final Locale systemLocale = ServiceLocator.getInstance().getSystemLocaleList().get(0);
        final LocalDateTime date = new ISODateParser(systemLocale)
                .parse(library.getLastSyncDateAsString()).orElse(null);
        updateSyncDate(date);

        vb.archiveContent.setText(getString(R.string.name_colon_value,
                                            getString(R.string.lbl_books),
                                            String.valueOf(library.getTotalBooks())));

        vm.getExtraArgs().putParcelable(CalibreContentServer.BKEY_LIBRARY, library);
    }

    private void onSyncDateSet(@NonNull final int[] fieldIds,
                               @NonNull final Long[] selections) {
        // The arrays have either one field (which can be null) or none
        if (selections.length > 0) {
            if (selections[0] == null) {
                updateSyncDate(null);
            } else {
                final LocalDateTime sd = Instant.ofEpochMilli(selections[0])
                                                .atZone(ZoneId.systemDefault())
                                                .toLocalDateTime();
                updateSyncDate(sd);
            }
        }
    }

    private void updateSyncDateVisibility() {
        final DataReader.Updates updateOption = vm.getUpdateOption();
        final boolean showSyncDateField =
                vm.getSyncServer().isSyncDateUserEditable()
                && (updateOption == DataReader.Updates.Skip
                    || updateOption == DataReader.Updates.OnlyNewer);
        vb.infSyncDate.setVisibility(showSyncDateField ? View.VISIBLE : View.GONE);
        vb.lblSyncDate.setVisibility(showSyncDateField ? View.VISIBLE : View.GONE);
    }

    /**
     * The value stored in the ViewModel is what we'll actually use.
     * The field is display-only.
     *
     * @param lastSyncDate to use
     */
    private void updateSyncDate(@Nullable final LocalDateTime lastSyncDate) {
        vm.setSyncDate(lastSyncDate);
        //noinspection DataFlowIssue
        vb.syncDate.setText(DateUtils.displayDate(getContext(), lastSyncDate));
    }


    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.process(progress -> {
            if (progressDelegate == null) {
                //noinspection DataFlowIssue
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.lbl_importing)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                        .show(() -> getActivity().getWindow());
            }
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

    private void onImportCancelled(@NonNull final LiveDataEvent<ReaderResults> message) {
        closeProgressDialog();

        message.process(optReaderResults -> {
            if (optReaderResults != null) {
                onImportFinished(R.string.info_import_partially_complete, optReaderResults);
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
    private void onImportFinished(@NonNull final LiveDataEvent<ReaderResults> message) {
        closeProgressDialog();

        message.process(readerResults ->
                                onImportFinished(R.string.info_import_complete, readerResults));
    }

    /**
     * Import finished/cancelled: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports success or cancelled.
     * @param result  of the import
     */
    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ReaderResults result) {
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(titleId)
                .setMessage(createReport(result))
                .setPositiveButton(R.string.action_done, (d, w) -> {
                    final Intent resultIntent = SyncContractBase
                            .createResult(SyncContractBase.Outcome.Read);
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
    private String createReport(@NonNull final ReaderResults result) {
        //noinspection DataFlowIssue
        return String.join("\n", result.createReport(getContext()));
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }
}
