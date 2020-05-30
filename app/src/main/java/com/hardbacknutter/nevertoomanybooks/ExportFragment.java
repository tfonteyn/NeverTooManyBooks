/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ExportTaskModel;

/**
 * TODO: consider using true background tasks without progress dialog and have them report
 * being finished/failed via a notification. That would allow the user to continue using
 * the app while the export is running.
 */
public class ExportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ExportFragment";
    /** ViewModel. */
    private ResultDataModel mResultDataModel;
    /** Export. */
    private ExportTaskModel mExportModel;
    private final OptionsDialogBase.OptionsListener<ExportManager> mExportOptionsListener =
            new OptionsDialogBase.OptionsListener<ExportManager>() {
                @Override
                public void onOptionsSet(@NonNull final ExportManager options) {
                    exportPickUri(options);
                }

                @Override
                public void onCancelled() {
                    //noinspection ConstantConditions
                    getActivity().finish();
                }
            };
    @Nullable
    private ProgressDialogFragment mProgressDialog;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        mExportModel = new ViewModelProvider(this).get(ExportTaskModel.class);
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

        mExportModel.onTaskProgress().observe(getViewLifecycleOwner(), this::onTaskProgress);
        mExportModel.onTaskFinished().observe(getViewLifecycleOwner(), this::onExportFinished);
        exportShowOptions();
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
            Log.d(getClass().getName(), "onAttachFragment: " + childFragment.getTag());
        }
        super.onAttachFragment(childFragment);

        if (childFragment instanceof ExportHelperDialogFragment) {
            ((ExportHelperDialogFragment) childFragment).setListener(mExportOptionsListener);
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
        // collect all data for passing to the calling Activity
        if (data != null) {
            mResultDataModel.putResultData(data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.EXPORT_PICK_URI: {
                // The user selected a file to backup to. Next step starts the export task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    final Uri uri = data.getData();
                    if (uri != null) {
                        mExportModel.startArchiveExportTask(uri);
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

    private void onTaskProgress(@NonNull final TaskListener.ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog();
        }
        mProgressDialog.onProgress(message);
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog() {
        FragmentManager fm = getChildFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        // not found? create it
        if (dialog == null) {
            dialog = ProgressDialogFragment.newInstance(R.string.lbl_backing_up, false, true, 0);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mExportModel.getTask());
        return dialog;
    }

    /**
     * Export Step 1: show the options to the user.
     */
    private void exportShowOptions() {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.txt_export_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    //noinspection ConstantConditions
                    getActivity().finish();
                })
                .setNeutralButton(R.string.btn_options, (d, w) -> ExportHelperDialogFragment
                        .newInstance()
                        //TEST: screen rotation
                        .show(getChildFragmentManager(), ExportHelperDialogFragment.TAG))
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        exportPickUri(new ExportManager(Options.ALL)))
                .create()
                .show();
    }

    /**
     * Export Step 2: prompt the user for a uri to export to.
     *
     * @param helper export configuration
     */
    private void exportPickUri(@NonNull final ExportManager helper) {
        // save the configured helper
        mExportModel.setHelper(helper);
        //noinspection ConstantConditions
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_TITLE, mExportModel.getDefaultUriName(getContext()));
        startActivityForResult(intent, RequestCode.EXPORT_PICK_URI);
    }

    /**
     * Export finished/failed: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(@NonNull final TaskListener.FinishMessage<ExportManager>
                                          message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.status) {
            case Success: {
                // sanity check
                Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);

                //noinspection ConstantConditions
                MaterialAlertDialogBuilder dialogBuilder =
                        new MaterialAlertDialogBuilder(getContext())
                                .setIcon(R.drawable.ic_info)
                                .setTitle(R.string.progress_end_backup_success)
                                .setPositiveButton(R.string.done, (d, which) -> {
                                    //noinspection ConstantConditions
                                    getActivity().finish();
                                });

                final Uri uri = message.result.getUri();
                final Pair<String, Long> uriInfo = FileUtils.getUriInfo(getContext(), uri);
                String msg = message.result.getResults().createReport(getContext(), uriInfo);
                if (message.result.offerEmail(uriInfo)) {
                    msg += "\n\n" + getString(R.string.confirm_email_export);
                    dialogBuilder.setNeutralButton(R.string.btn_email,
                                                   (d, which) -> onExportEmail(uri));
                }

                dialogBuilder
                        .setMessage(msg)
                        .create()
                        .show();

                //noinspection ConstantConditions
                getActivity().setResult(Activity.RESULT_OK, mResultDataModel.getResultData());
                break;
            }
            case Cancelled: {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.progress_end_cancelled,
                              Snackbar.LENGTH_LONG).show();
                //noinspection ConstantConditions
                getActivity().finish();
                break;
            }
            case Failed: {
                // sanity check
                Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
                //noinspection ConstantConditions
                String msg = message.result.createExceptionReport(getContext(), message.exception);
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_error)
                        .setTitle(R.string.error_backup_failed)
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                        .create()
                        .show();
                break;
            }
        }
    }

    /**
     * Create and send an email with the specified Uri.
     *
     * @param uri for the file to email
     */
    private void onExportEmail(@NonNull final Uri uri) {

        final String subject = '[' + getString(R.string.app_name) + "] "
                               + getString(R.string.lbl_books);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(uri);
        try {
            final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType("plain/text")
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, getString(R.string.lbl_send_mail)));
            //noinspection ConstantConditions
            getActivity().finish();

        } catch (@NonNull final NullPointerException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_error)
                    .setMessage(R.string.error_email_failed)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }
}
