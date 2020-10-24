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
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveExportTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.LiveDataEvent;

public class ExportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ExportFragment";
    /** FragmentResultListener request key. */
    private static final String RK_EXPORT_HELPER = ExportHelperDialogFragment.TAG + ":rk:";

    /** Export. */
    private ArchiveExportTask mArchiveExportTask;
    private final FragmentResultListener mExportOptionsListener =
            new OptionsDialogBase.OnOptionsListener<ExportManager>() {
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

        getChildFragmentManager()
                .setFragmentResultListener(RK_EXPORT_HELPER, this, mExportOptionsListener);
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
        getActivity().setTitle(R.string.lbl_backup);

        mArchiveExportTask = new ViewModelProvider(this).get(ArchiveExportTask.class);
        mArchiveExportTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mArchiveExportTask.onCancelled().observe(getViewLifecycleOwner(), this::onExportCancelled);
        mArchiveExportTask.onFailure().observe(getViewLifecycleOwner(), this::onExportFailure);
        mArchiveExportTask.onFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        // if the task is NOT already running (e.g. after a screen rotation...) show the options
        if (!mArchiveExportTask.isRunning()) {
            exportShowOptions();
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
            case RequestCode.EXPORT_PICK_URI: {
                // The user selected a file to backup to. Next step starts the export task.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, "data");
                    final Uri uri = data.getData();
                    if (uri != null) {
                        mArchiveExportTask.startExport(uri);
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

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog() {
        final FragmentManager fm = getChildFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);
        // not found? create it
        if (dialog == null) {
            dialog = ProgressDialogFragment.newInstance(
                    getString(R.string.lbl_backing_up), false, true);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mArchiveExportTask);
        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    /**
     * Export Step 1: show the options to the user.
     */
    private void exportShowOptions() {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.txt_export_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                .setNeutralButton(R.string.btn_options, (d, w) -> {
                    d.dismiss();
                    ExportHelperDialogFragment
                            .newInstance(RK_EXPORT_HELPER)
                            .show(getChildFragmentManager(), ExportHelperDialogFragment.TAG);
                })
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        exportPickUri(new ExportManager(Options.ENTITIES)))
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
        mArchiveExportTask.setHelper(helper);
        //noinspection ConstantConditions
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_TITLE, mArchiveExportTask.getDefaultUriName(getContext()));
        startActivityForResult(intent, RequestCode.EXPORT_PICK_URI);
    }

    private void onExportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_error)
                    .setTitle(R.string.error_backup_failed)
                    .setMessage(ExportManager.createErrorReport(getContext(), message.result))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    private void onExportCancelled(@NonNull final LiveDataEvent message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG).show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
        }
    }

    /**
     * Export finished/failed: Process the result.
     *
     * @param message to process
     */
    private void onExportFinished(@NonNull final FinishedMessage<ExportManager> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            //noinspection ConstantConditions
            final MaterialAlertDialogBuilder dialogBuilder =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_info)
                            .setTitle(R.string.progress_end_backup_success)
                            .setPositiveButton(R.string.done, (d, which) -> getActivity().finish());

            final Uri uri = message.result.getUri();
            final Pair<String, Long> uriInfo = FileUtils.getUriInfo(getContext(), uri);
            final String report = message.result.getResults().createReport(getContext(), uriInfo);
            if (message.result.offerEmail(uriInfo)) {
                dialogBuilder
                        .setMessage(report + "\n\n" + getString(R.string.confirm_email_export))
                        .setNeutralButton(R.string.btn_email, (d, which) ->
                                onExportEmail(uri, report));
            } else {
                dialogBuilder.setMessage(report);
            }

            dialogBuilder.create()
                         .show();
        }
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
