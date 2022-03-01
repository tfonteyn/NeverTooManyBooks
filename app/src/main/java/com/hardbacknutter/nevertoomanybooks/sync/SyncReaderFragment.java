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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SyncContractBase;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncImportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.datepicker.DatePickerListener;
import com.hardbacknutter.nevertoomanybooks.widgets.datepicker.SingleDatePicker;

public class SyncReaderFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SyncReaderFragment";

    private ToolbarMenuProvider mToolbarMenuProvider;

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    getActivity().finish();
                }
            };

    /** The ViewModel. */
    private SyncReaderViewModel mVm;

    /** View Binding. */
    private FragmentSyncImportBinding mVb;

    private SingleDatePicker mSyncDatePicker;

    @Nullable
    private ProgressDelegate mProgressDelegate;

    @SuppressWarnings("TypeMayBeWeakened")
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

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(SyncReaderViewModel.class);
        mVm.init(requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentSyncImportBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(mVm.getSyncReaderHelper().getSyncServer().getLabel());

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mSyncDatePicker = new SingleDatePicker(getChildFragmentManager(),
                                               R.string.lbl_sync_date,
                                               mVb.lblSyncDate.getId());

        mVm.onReadMetaDataFinished().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        mVm.onReadMetaDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onReadDataCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        mVm.onReadDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);
        mVm.onReadDataFinished().observe(getViewLifecycleOwner(), this::onImportFinished);

        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mVm.getSyncReaderHelper().setRecordType(isChecked, RecordType.Books);
            mVb.rbBooksGroup.setEnabled(isChecked);
        });
        mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> mVm
                .getSyncReaderHelper().setRecordType(isChecked, RecordType.Cover));

        mVb.infImportNewOnly.setOnClickListener(StandardDialogs::infoPopup);
        mVb.infImportNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);
        mVb.infImportAll.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            final SyncReaderHelper helper = mVm.getSyncReaderHelper();
            if (checkedId == mVb.rbImportNewOnly.getId()) {
                helper.setUpdateOption(DataReader.Updates.Skip);
            } else if (checkedId == mVb.rbImportNewAndUpdated.getId()) {
                helper.setUpdateOption(DataReader.Updates.OnlyNewer);
            } else if (checkedId == mVb.rbImportAll.getId()) {
                helper.setUpdateOption(DataReader.Updates.Overwrite);
            }

            updateSyncDateVisibility();
        });

        mVb.syncDate.setOnClickListener(v -> mSyncDatePicker.launch(
                mVm.getSyncReaderHelper().getSyncDate(), this::onSyncDateSet));

        if (!mVm.isRunning()) {
            showOptions();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSyncDatePicker.onResume(this::onSyncDateSet);
    }

    /**
     * Update the screen with specific options and values.
     */
    private void showOptions() {
        final SyncReaderHelper helper = mVm.getSyncReaderHelper();

        final Optional<SyncReaderMetaData> metaData = helper.getMetaData();
        if (metaData.isPresent()) {
            showMetaData(metaData.get());
        } else {
            mVm.startReadingMetaData();
            showMetaData(null);
        }

        final Set<RecordType> recordTypes = helper.getRecordTypes();
        mVb.cbxBooks.setEnabled(false);
        mVb.cbxBooks.setChecked(recordTypes.contains(RecordType.Books));
        mVb.cbxCovers.setChecked(recordTypes.contains(RecordType.Cover));

        final DataReader.Updates updateOption = helper.getUpdateOption();
        mVb.rbImportNewOnly.setChecked(updateOption == DataReader.Updates.Skip);
        mVb.rbImportAll.setChecked(updateOption == DataReader.Updates.Overwrite);
        mVb.rbImportNewAndUpdated.setChecked(updateOption == DataReader.Updates.OnlyNewer);

        final boolean hasLastUpdateDateField = helper.getSyncServer().hasLastUpdateDateField();
        mVb.rbImportNewAndUpdated.setEnabled(hasLastUpdateDateField);
        mVb.infImportNewAndUpdated.setEnabled(hasLastUpdateDateField);

        updateSyncDateVisibility();

        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void onMetaDataRead(@NonNull final
                                LiveDataEvent<TaskResult<Optional<SyncReaderMetaData>>> message) {
        message.getData().flatMap(TaskResult::requireResult).ifPresent(this::showMetaData);
    }

    /**
     * Display the name of the server + any valid data we can get from it.
     */
    private void showMetaData(@Nullable final SyncReaderMetaData metaData) {
        // do this here, as the menu depends on the meta-data having been fetched
        mToolbarMenuProvider.onPrepareMenu(getToolbar().getMenu());

        if (metaData == null) {
            mVb.archiveContent.setVisibility(View.INVISIBLE);
            mVb.lblCalibreLibrary.setVisibility(View.GONE);
        } else {
            final SyncReaderHelper helper = mVm.getSyncReaderHelper();
            switch (helper.getSyncServer()) {
                case CalibreCS: {
                    mVb.archiveContent.setVisibility(View.VISIBLE);
                    mVb.lblCalibreLibrary.setVisibility(View.VISIBLE);
                    showCalibreMetaData(metaData.getData());
                    break;
                }
                case StripInfo:
                default: {
                    mVb.archiveContent.setVisibility(View.INVISIBLE);
                    mVb.lblCalibreLibrary.setVisibility(View.GONE);
                    break;
                }
            }
        }
    }

    private void showCalibreMetaData(@NonNull final Bundle data) {

        final ArrayList<CalibreLibrary> libraries =
                data.getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST);

        //noinspection ConstantConditions
        if (libraries.size() == 1) {
            // shortcut...
            onCalibreLibrarySelected(libraries.get(0));

        } else {
            //noinspection ConstantConditions
            final ExtArrayAdapter<CalibreLibrary> adapter =
                    new EntityArrayAdapter<>(getContext(), libraries);

            mVb.calibreLibrary.setAdapter(adapter);
            mVb.calibreLibrary.setOnItemClickListener(
                    (av, v, position, id) -> onCalibreLibrarySelected(libraries.get(position)));

            CalibreLibrary library = mVm.getSyncReaderHelper().getExtraArgs()
                                        .getParcelable(CalibreContentServer.BKEY_LIBRARY);
            if (library == null) {
                library = data.getParcelable(CalibreContentServer.BKEY_LIBRARY);
            }
            //noinspection ConstantConditions
            onCalibreLibrarySelected(library);
        }
    }

    private void onCalibreLibrarySelected(@NonNull final CalibreLibrary library) {
        mVb.calibreLibrary.setText(library.getName(), false);

        updateSyncDate(library.getLastSyncDate());

        mVb.archiveContent.setText(getString(R.string.name_colon_value,
                                             getString(R.string.lbl_books),
                                             String.valueOf(library.getTotalBooks())));

        mVm.getSyncReaderHelper().getExtraArgs()
           .putParcelable(CalibreContentServer.BKEY_LIBRARY, library);

        // do this again, as the selection will affect the menu
        mToolbarMenuProvider.onPrepareMenu(getToolbar().getMenu());
    }

    private void onSyncDateSet(@NonNull final int[] fieldIds,
                               @NonNull final long[] selections) {
        if (selections.length > 0) {
            if (selections[0] == DatePickerListener.NO_SELECTION) {
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
        final SyncReaderHelper helper = mVm.getSyncReaderHelper();
        final DataReader.Updates updateOption = helper.getUpdateOption();
        final boolean showSyncDateField =
                helper.getSyncServer().isSyncDateUserEditable()
                && (updateOption == DataReader.Updates.Skip
                    || updateOption == DataReader.Updates.OnlyNewer);
        mVb.infSyncDate.setVisibility(showSyncDateField ? View.VISIBLE : View.GONE);
        mVb.lblSyncDate.setVisibility(showSyncDateField ? View.VISIBLE : View.GONE);
    }

    /**
     * the value stored in the ViewModel is what we'll actually use
     * the field is display-only
     *
     * @param lastSyncDate to use
     */
    private void updateSyncDate(@Nullable final LocalDateTime lastSyncDate) {
        mVm.getSyncReaderHelper().setSyncDate(lastSyncDate);
        //noinspection ConstantConditions
        mVb.syncDate.setText(DateUtils.displayDate(getContext(), lastSyncDate));
    }


    private void onProgress(@NonNull final LiveDataEvent<TaskProgress> message) {
        message.getData().ifPresent(data -> {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(R.string.lbl_importing)
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> mVm.cancelTask(data.taskId))
                        .show(getActivity().getWindow());
            }
            mProgressDelegate.onProgress(data);
        });
    }

    private void onImportFailure(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, data.getResult())
                                    .orElse(getString(R.string.error_unknown));

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        });
    }

    private void onImportCancelled(@NonNull final
                                   LiveDataEvent<TaskResult<ReaderResults>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final ReaderResults result = data.getResult();
            if (result != null) {
                onImportFinished(R.string.progress_end_import_partially_complete, result);
            } else {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG).show();
                //noinspection ConstantConditions
                getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
            }
        });
    }

    /**
     * Import finished: Step 1: Process the message.
     *
     * @param message to process
     */
    private void onImportFinished(@NonNull final LiveDataEvent<TaskResult<ReaderResults>> message) {
        closeProgressDialog();

        message.getData().map(TaskResult::requireResult).ifPresent(
                result -> onImportFinished(R.string.progress_end_import_complete, result));
    }

    /**
     * Import finished/cancelled: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports success or cancelled.
     * @param result  of the import
     */
    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ReaderResults result) {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_info_24)
                .setTitle(titleId)
                .setMessage(createReport(result))
                .setPositiveButton(R.string.action_done, (d, w) -> {
                    final Intent resultIntent = new Intent()
                            .putExtra(SyncContractBase.BKEY_RESULT,
                                      SyncContractBase.RESULT_READ_DONE);
                    //noinspection ConstantConditions
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

        return items.stream()
                    .map(s -> getString(R.string.list_element, s))
                    .collect(Collectors.joining("\n"));
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
            menuItem.setEnabled(false);
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
                mVm.startReadingData();
                return true;
            }
            return false;
        }
    }

}
