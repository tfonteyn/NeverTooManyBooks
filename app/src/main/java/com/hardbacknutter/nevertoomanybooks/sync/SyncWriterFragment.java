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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncExportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

public class SyncWriterFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SyncWriterFragment";

    /** The ViewModel. */
    private SyncWriterViewModel mVm;

    /** View Binding. */
    private FragmentSyncExportBinding mVb;

    @NonNull
    private final MenuProvider mToolbarMenuProvider = new ToolbarMenuProvider();

    @Nullable
    private ProgressDelegate mProgressDelegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(SyncWriterViewModel.class);
        mVm.init(requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentSyncExportBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(mVm.getSyncServer().getLabel());

        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onExportCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        mVm.onExportFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        mVm.onExportFinished().observe(getViewLifecycleOwner(), this::onExportFinished);


        // Check if the task is already running (e.g. after a screen rotation...)
        // Note that after a screen rotation, the full-options screen will NOT be re-shown.
        if (!mVm.isExportRunning()) {
            // The task is NOT yet running.
            // Show either the full-options screen or the quick-options dialog
            if (mVm.isQuickOptionsAlreadyShown()) {
                showOptions();
            } else {
                mVm.setQuickOptionsAlreadyShown(true);
                showQuickOptions();
            }
        }
    }

    private void showQuickOptions() {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(mVm.getSyncServer().getLabel())
                .setMessage(R.string.action_synchronize)
                .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                .setNeutralButton(R.string.btn_options, (d, w) -> {
                    d.dismiss();
                    showOptions();
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    d.dismiss();
                    mVm.startExport();
                })
                .create()
                .show();
    }

    /**
     * Export Step 1b: Show the full options screen to the user.
     */
    private void showOptions() {
        final SyncWriterConfig config = mVm.getConfig();
        final Set<RecordType> exportEntities = config.getExporterEntries();

        mVb.cbxCovers.setChecked(exportEntities.contains(RecordType.Cover));
        mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> config
                .setExportEntry(RecordType.Cover, isChecked));

        final boolean incremental = config.isIncremental();
        mVb.rbExportBooksOptionAll.setChecked(!incremental);
        mVb.rbExportBooksOptionNewAndUpdated.setChecked(incremental);
        mVb.infExportBooksOptionNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);
        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> config
                .setIncremental(checkedId == mVb.rbExportBooksOptionNewAndUpdated.getId()));

        mVb.cbxBooks.setChecked(true);
        mVb.cbxBooks.setEnabled(true);

        mVb.rbExportBooksOptionNewAndUpdated.setChecked(true);

        mVb.cbxDeleteRemovedBooks.setOnCheckedChangeListener(
                (v, isChecked) -> mVm.getConfig().setDeleteLocalBooks(isChecked));

        mVb.getRoot().setVisibility(View.VISIBLE);
    }


    private void onExportCancelled(
            @NonNull final LiveDataEvent<TaskResult<SyncWriterResults>> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
        }
    }

    private void onExportFailure(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, message.getData().getResult())
                                    .orElse(getString(R.string.error_unknown));

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_export_failed)
                    .setMessage(msg)
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
    private void onExportFinished(
            @NonNull final LiveDataEvent<TaskResult<SyncWriterResults>> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final SyncWriterResults results = message.getData().requireResult();

            final List<String> items = extractExportedItems(results);
            if (items.isEmpty()) {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.menu_backup_and_export)
                        .setMessage(R.string.warning_no_matching_book_found)
                        .setPositiveButton(R.string.action_done, (d, w) -> getActivity().finish())
                        .create()
                        .show();
            } else {

                final String itemList = items
                        .stream()
                        .map(s -> getString(R.string.list_element, s))
                        .collect(Collectors.joining("\n"));

                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_baseline_info_24)
                        .setTitle(R.string.progress_end_export_successful)
                        .setMessage(itemList)
                        .setPositiveButton(R.string.action_done, (d, w) -> getActivity().finish())
                        .create()
                        .show();
            }
        }
    }

    @NonNull
    private List<String> extractExportedItems(@NonNull final SyncWriterResults result) {
        final List<String> items = new ArrayList<>();

        if (result.booksWritten > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_books),
                                String.valueOf(result.booksWritten)));
        }
        if (result.coversWritten > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_covers),
                                String.valueOf(result.coversWritten)));
        }
        return items;
    }

    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.menu_backup_and_export)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> mVm.cancelTask(message.getData().taskId))
                        .show(getActivity().getWindow());
            }
            mProgressDelegate.onProgress(message.getData());
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
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_ACTION_CONFIRM) {
                if (mVm.getConfig().getExporterEntries().size() > 1) {
                    mVm.startExport();
                }
                return true;
            }
            return false;
        }
    }

}
