/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainer;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveImportTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;

public class ImportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ImportFragment";
    /** FragmentResultListener request key. */
    private static final String RK_IMPORT_HELPER = ImportHelperDialogFragment.TAG + ":rk:";
    /** Import. */
    private ArchiveImportTask mArchiveImportTask;
    private final FragmentResultListener mImportOptionsListener =
            new OptionsDialogBase.OnOptionsListener<ImportManager>() {
                @Override
                public void onOptionsSet(@NonNull final ImportManager options) {
                    mArchiveImportTask.startImport(options);
                }

                @Override
                public void onCancelled() {
                    //noinspection ConstantConditions
                    getActivity().finish();
                }
            };
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** ViewModel. */
    private ResultDataModel mResultData;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getChildFragmentManager()
                .setFragmentResultListener(RK_IMPORT_HELPER, this, mImportOptionsListener);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_import);

        mResultData = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        mArchiveImportTask = new ViewModelProvider(this).get(ArchiveImportTask.class);
        mArchiveImportTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mArchiveImportTask.onCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        mArchiveImportTask.onFailure().observe(getViewLifecycleOwner(), this::onImportFailure);
        mArchiveImportTask.onFinished().observe(getViewLifecycleOwner(), this::onImportFinished);

        // if the task is NOT already running (e.g. after a screen rotation...) show the options
        if (!mArchiveImportTask.isRunning()) {
            importPickUri();
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.IMPORT_PICK_URI: {
                // The user selected a file to import from. Next step asks for the options.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, "data");
                    final Uri uri = data.getData();
                    if (uri != null) {
                        importShowOptions(uri);
                    }
                } else {
                    //noinspection ConstantConditions
                    getActivity().finish();
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog();
        }
        mProgressDialog.onProgress(message);
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
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
                    getString(R.string.lbl_importing), false, true);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mArchiveImportTask);

        return dialog;
    }

    /**
     * Import Step 1: prompt the user for a uri to export to.
     */
    private void importPickUri() {
        // Import
        // This does not allow multiple saved files like "foo.tar (1)", "foo.tar (2)"
        // String[] mimeTypes = {"application/x-tar", "text/csv"};
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                // .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                .setType("*/*");
        startActivityForResult(intent, RequestCode.IMPORT_PICK_URI);
    }

    /**
     * Import Step 2: show the options to the user.
     *
     * @param uri file to read from
     */
    private void importShowOptions(@NonNull final Uri uri) {
        final ImportManager helper = new ImportManager(uri);

        //noinspection ConstantConditions
        if (!helper.isSupported(getContext())) {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_error)
                    .setMessage(R.string.error_import_file_not_supported)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
            return;
        }

        final ArchiveContainer container = helper.getContainer(getContext());
        //noinspection EnumSwitchStatementWhichMissesCases
        switch (container) {
            case CsvBooks:
                helper.setOptions(Options.BOOKS | Options.IS_SYNC);

                //URGENT: make a backup before ANY csv import!
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.lbl_import_book_data)
                        .setMessage(R.string.warning_import_be_cautious)
                        .setNegativeButton(android.R.string.cancel,
                                           (d, w) -> getActivity().finish())
                        .setPositiveButton(android.R.string.ok, (d, w) -> ImportHelperDialogFragment
                                .newInstance(RK_IMPORT_HELPER, helper)
                                .show(getChildFragmentManager(), ImportHelperDialogFragment.TAG))
                        .create()
                        .show();

                break;

            case Zip:
            case Tar:
            case SqLiteDb:
                helper.setOptions(Options.ENTITIES | Options.IS_SYNC);
                ImportHelperDialogFragment
                        .newInstance(RK_IMPORT_HELPER, helper)
                        .show(getChildFragmentManager(), ImportHelperDialogFragment.TAG);
                break;

            default:
                throw new IllegalArgumentException(String.valueOf(container));
        }
    }

    private void onImportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_error)
                    .setTitle(R.string.error_import_failed)
                    .setMessage(ImportManager.createErrorReport(getContext(), message.result))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    private void onImportCancelled(@NonNull final FinishedMessage<ImportManager> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            if (message.result != null) {
                onImportFinished(R.string.progress_end_import_partially_complete,
                                 message.result);
            } else {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG)
                        .show();
                //noinspection ConstantConditions
                getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
            }
        }
    }

    /**
     * Import finished/failed: Step 1: Process the result.
     *
     * @param message to process
     */
    private void onImportFinished(@NonNull final FinishedMessage<ImportManager> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            onImportFinished(R.string.progress_end_import_complete, message.result);
        }
    }

    /**
     * Import finished: Step 2: Inform the user.
     *
     * @param titleId       for the dialog title; reports success or cancelled.
     * @param importManager details of the import
     */
    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ImportManager importManager) {

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_info)
                .setTitle(titleId)
                .setMessage(importManager.getResults().createReport(getContext()))
                .setPositiveButton(R.string.done, (d, w) -> {
                    mResultData.putResultData(ImportResults.BKEY_IMPORT_RESULTS,
                                              importManager.getOptions());
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mResultData.getResultIntent());
                    getActivity().finish();
                })
                .create()
                .show();
    }

}
