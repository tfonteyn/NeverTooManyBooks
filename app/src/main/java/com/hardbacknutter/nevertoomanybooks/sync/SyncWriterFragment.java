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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncExportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.ErrorDialog;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;

public class SyncWriterFragment
        extends BaseFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "SyncWriterFragment";
    /** The ViewModel. */
    private SyncWriterViewModel vm;
    /** View Binding. */
    private FragmentSyncExportBinding vb;
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
        final Fragment fragment = new SyncWriterFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(SyncServer.BKEY_SITE, syncServer);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(getActivity()).get(SyncWriterViewModel.class);
        vm.init(requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentSyncExportBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection DataFlowIssue
        getToolbar().setTitle(vm.getDestinationDisplayName(getContext()));

        vm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        vm.onWriteDataCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        vm.onWriteDataFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        vm.onWriteDataFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        vb.cbxBooks.setChecked(true);
        vb.cbxBooks.setEnabled(true);

        vb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> vm
                .setRecordType(isChecked, RecordType.Cover));

        vb.infExportNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);

        vb.rbgBooks.setOnCheckedChangeListener((group, checkedId) -> vm
                .setIncremental(checkedId == vb.rbExportNewAndUpdated.getId()));

        vb.cbxDeleteRemovedBooks.setOnCheckedChangeListener((v, isChecked) -> vm
                .setDeleteLocalBooks(isChecked));

        final FloatingActionButton fab = getFab();
        fab.setImageResource(R.drawable.ic_baseline_export);
        // GONE here; will be made visible in showOptions() together with the full UI.
        fab.setVisibility(View.GONE);
        fab.setOnClickListener(v -> startWriting());

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

        final Context context = getContext();
        //noinspection DataFlowIssue
        new MaterialAlertDialogBuilder(context)
                .setTitle(vm.getDestinationDisplayName(context))
                .setMessage(R.string.action_synchronize)
                .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                .setNeutralButton(R.string.action_show_options, (d, w) -> {
                    d.dismiss();
                    showOptions();
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    d.dismiss();
                    vm.startExport();
                })
                .create()
                .show();
    }

    /**
     * Show the full options screen to the user.
     */
    private void showOptions() {
        final Set<RecordType> recordTypes = vm.getRecordTypes();
        vb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));

        final boolean incremental = vm.isIncremental();
        vb.rbExportAll.setChecked(!incremental);
        vb.rbExportNewAndUpdated.setChecked(incremental);

        getFab().setVisibility(View.VISIBLE);
        vb.getRoot().setVisibility(View.VISIBLE);
    }

    private void startWriting() {
        if (vm.isReadyToGo()) {
            vm.startExport();
        } else {
            //noinspection DataFlowIssue
            Snackbar.make(getView(), R.string.warning_nothing_selected, Snackbar.LENGTH_SHORT)
                    .show();
        }
    }

    private void onExportCancelled(@NonNull final LiveDataEvent<SyncWriterResults> message) {
        closeProgressDialog();

        message.process(ignored -> showMessageAndFinishActivity(getString(R.string.cancelled)));
    }

    private void onExportFailure(@NonNull final LiveDataEvent<Throwable> message) {
        closeProgressDialog();

        message.process(e -> {
            //noinspection DataFlowIssue
            ErrorDialog.show(getContext(), TAG, e, getString(R.string.error_export_failed),
                             (d, w) -> getActivity().finish());
        });
    }

    /**
     * Export finished/failed: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(@NonNull final LiveDataEvent<SyncWriterResults> message) {
        closeProgressDialog();

        message.process(writerResults -> {
            final List<String> items = extractExportedItems(writerResults);
            if (items.isEmpty()) {
                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.title_backup_and_export)
                        .setMessage(R.string.warning_no_matching_book_found)
                        .setPositiveButton(R.string.action_done, (d, w) -> {
                            //noinspection DataFlowIssue
                            getActivity().setResult(Activity.RESULT_OK);
                            getActivity().finish();
                        })
                        .create()
                        .show();
            } else {
                final String itemList = items
                        .stream()
                        .map(s -> getString(R.string.list_element, s))
                        .collect(Collectors.joining("\n"));

                //noinspection DataFlowIssue
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.info_export_successful)
                        .setMessage(itemList)
                        .setPositiveButton(R.string.action_done, (d, w) -> {
                            final Intent resultIntent = SyncContractBase
                                    .createResult(SyncContractBase.Outcome.Write);
                            //noinspection DataFlowIssue
                            getActivity().setResult(Activity.RESULT_OK, resultIntent);
                            getActivity().finish();
                        })
                        .create()
                        .show();
            }
        });
    }

    @NonNull
    private List<String> extractExportedItems(@NonNull final SyncWriterResults result) {
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
        return items;
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.process(progress -> {
            if (progressDelegate == null) {
                //noinspection DataFlowIssue
                progressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.title_backup_and_export)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> vm.cancelTask(progress.taskId))
                        .show(() -> getActivity().getWindow());
            }
            progressDelegate.onProgress(progress);
        });
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection DataFlowIssue
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }
}
