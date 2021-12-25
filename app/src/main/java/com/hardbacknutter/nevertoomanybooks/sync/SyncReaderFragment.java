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
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.common.RecordType;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncImportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.ReaderResults;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.datepicker.DatePickerListener;
import com.hardbacknutter.nevertoomanybooks.widgets.datepicker.SingleDatePicker;

public class SyncReaderFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "SyncReaderFragment";

    /** The ViewModel. */
    protected SyncReaderViewModel mVm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mVm.getResultIntent());
                    getActivity().finish();
                }
            };
    /** View Binding. */
    private FragmentSyncImportBinding mVb;

    private SingleDatePicker mSyncDatePicker;

    @Nullable
    private ProgressDelegate mProgressDelegate;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

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

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mSyncDatePicker = new SingleDatePicker(getChildFragmentManager(),
                                               R.string.lbl_sync_date,
                                               mVb.lblSyncDate.getId());

        mVm.onMetaDataRead().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        mVm.onMetaDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onImportCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        mVm.onImportFailure().observe(getViewLifecycleOwner(), this::onImportFailure);
        mVm.onImportFinished().observe(getViewLifecycleOwner(), this::onImportFinished);

        setTitle(mVm.getSyncServer().getLabel());
        // Either first time, or if the task is already running - either is fine.
        showOptions();
    }

    @Override
    public void onResume() {
        super.onResume();
        mSyncDatePicker.onResume(this::onSyncDateSet);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_action_go, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        menuItem.setEnabled(false);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);

        final MenuItem menuItem = getToolbar().getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
        menuItem.setEnabled(mVm.isReadyToGo());
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            mVm.startImport();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Update the screen with specific options and values.
     */
    private void showOptions() {
        showAvailableInfo();
        if (mVm.getMetaData() == null) {
            mVm.readMetaData();
        }

        final SyncReaderConfig config = mVm.getConfig();

        final Set<RecordType> entries = config.getImportEntries();

        mVb.cbxBooks.setEnabled(false);
        mVb.cbxBooks.setChecked(entries.contains(RecordType.Books));
        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setImportEntry(RecordType.Books, isChecked);
            mVb.rbBooksGroup.setEnabled(isChecked);
        });

        mVb.cbxCovers.setChecked(entries.contains(RecordType.Cover));
        mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> config
                .setImportEntry(RecordType.Cover, isChecked));

        mVb.rbImportBooksOptionNewOnly.setChecked(mVm.isNewBooksOnly());
        mVb.infImportBooksOptionNewOnly.setOnClickListener(StandardDialogs::infoPopup);

        final boolean hasLastUpdateDateField = mVm.getSyncServer().hasLastUpdateDateField();
        mVb.rbImportBooksOptionNewAndUpdated.setEnabled(hasLastUpdateDateField);
        mVb.rbImportBooksOptionNewAndUpdated.setChecked(mVm.isNewAndUpdatedBooks());
        mVb.infImportBooksOptionNewAndUpdated.setEnabled(hasLastUpdateDateField);
        mVb.infImportBooksOptionNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbImportBooksOptionAll.setChecked(mVm.isAllBooks());
        mVb.infImportBooksOptionAll.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == mVb.rbImportBooksOptionNewOnly.getId()) {
                mVm.setNewBooksOnly();

            } else if (checkedId == mVb.rbImportBooksOptionNewAndUpdated.getId()) {
                mVm.setNewAndUpdatedBooks();

            } else if (checkedId == mVb.rbImportBooksOptionAll.getId()) {
                mVm.setAllBooks();
            }
            updateSyncDateVisibility();
        });

        updateSyncDateVisibility();
        mVb.syncDate.setOnClickListener(v -> mSyncDatePicker.launch(mVm.getSyncDate(),
                                                                    this::onSyncDateSet));

        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void onMetaDataRead(@NonNull final FinishedMessage<SyncReaderMetaData> message) {
        if (message.isNewEvent()) {
            mVm.setMetaData(message.getResult());
            showAvailableInfo();
        }
    }

    /**
     * Display the name of the server + any valid data we can get from it.
     */
    private void showAvailableInfo() {
        final SyncReaderMetaData metaData = mVm.getMetaData();
        if (metaData != null) {
            switch (mVm.getSyncServer()) {
                case CalibreCS: {
                    mVb.archiveContent.setVisibility(View.VISIBLE);
                    mVb.lblCalibreLibrary.setVisibility(View.VISIBLE);
                    showCalibreMetaData(metaData);
                    break;
                }
                case StripInfo:
                default: {
                    mVb.archiveContent.setVisibility(View.INVISIBLE);
                    mVb.lblCalibreLibrary.setVisibility(View.GONE);
                    break;
                }
            }
        } else {
            // no metadata at all, HIDE the content field, REMOVE the calibre field
            mVb.archiveContent.setVisibility(View.INVISIBLE);
            mVb.lblCalibreLibrary.setVisibility(View.GONE);
        }
    }

    private void showCalibreMetaData(@NonNull final SyncReaderMetaData metaData) {

        final ArrayList<CalibreLibrary> libraries = metaData
                .getBundle().getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST);

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

            CalibreLibrary library = mVm.getConfig().getExtraArgs()
                                        .getParcelable(CalibreContentServer.BKEY_LIBRARY);
            if (library == null) {
                library = metaData.getBundle().getParcelable(CalibreContentServer.BKEY_LIBRARY);
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

        mVm.getConfig().getExtraArgs()
           .putParcelable(CalibreContentServer.BKEY_LIBRARY, library);
    }

    private void updateSyncDateVisibility() {
        final boolean showSyncDateField = mVm.isSyncDateUserEditable();
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
        mVm.setSyncDate(lastSyncDate);
        //noinspection ConstantConditions
        mVb.syncDate.setText(DateUtils.displayDate(getContext(), lastSyncDate));
    }

    private void onImportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final Exception e = message.requireResult();

            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, e)
                                    .orElse(getString(R.string.error_unknown));

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    private void onImportCancelled(@NonNull final FinishedMessage<ReaderResults> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final ReaderResults result = message.getResult();
            if (result != null) {
                onImportFinished(R.string.progress_end_import_partially_complete, result);
            } else {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG)
                        .show();
                //noinspection ConstantConditions
                getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
            }
        }
    }

    /**
     * Import finished: Step 1: Process the message.
     *
     * @param message to process
     */
    private void onImportFinished(@NonNull final FinishedMessage<ReaderResults> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            onImportFinished(R.string.progress_end_import_complete, message.requireResult());
        }
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
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mVm.onImportFinished(result));
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

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(getProgressFrame())
                        .setTitle(getString(R.string.lbl_importing))
                        .setPreventSleep(true)
                        .setOnCancelListener(v -> mVm.cancelTask(message.taskId))
                        .show(getActivity().getWindow());
            }
            mProgressDelegate.onProgress(message);
        }
    }


    private void closeProgressDialog() {
        if (mProgressDelegate != null) {
            //noinspection ConstantConditions
            mProgressDelegate.dismiss(getActivity().getWindow());
            mProgressDelegate = null;
        }
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
}
