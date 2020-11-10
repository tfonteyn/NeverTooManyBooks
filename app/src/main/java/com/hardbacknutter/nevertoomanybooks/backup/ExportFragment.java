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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.HostingActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveExportTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

public class ExportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ExportFragment";
    /** FragmentResultListener request key. */
    private static final String RK_EXPORT_OPTIONS = TAG + ":rk:" + ExportOptionsDialogFragment.TAG;
    /**
     * The ViewModel and the {@link #mArchiveExportTask} could be folded into one object,
     * but we're trying to keep task logic separate for now.
     */
    private ExportViewModel mExportViewModel;
    private ArchiveExportTask mArchiveExportTask;
    /** The launcher for picking a Uri. */
    private final ActivityResultLauncher<String> mCreateDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(),
                                      this::onCreateDocument);
    private final FragmentResultListener mExportOptionsListener =
            (OptionsDialogBase.OnResultsListener) success -> {
                if (success) {
                    exportPickUri();
                } else {
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
                .setFragmentResultListener(RK_EXPORT_OPTIONS, this, mExportOptionsListener);
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

        mExportViewModel = new ViewModelProvider(getActivity()).get(ExportViewModel.class);

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
                    ExportOptionsDialogFragment
                            .newInstance(RK_EXPORT_OPTIONS)
                            .show(getChildFragmentManager(), ExportOptionsDialogFragment.TAG);
                })
                .setPositiveButton(android.R.string.ok, (d, w) -> exportPickUri())
                .create()
                .show();
    }

    /**
     * Export Step 2: prompt the user for a uri to export to.
     */
    private void exportPickUri() {
        // set it now, as we need it for mArchiveExportTask.getDefaultUriName call just below
        mArchiveExportTask.setHelper(mExportViewModel.getExportHelper());
        //noinspection ConstantConditions
        mCreateDocumentLauncher.launch(mArchiveExportTask.getDefaultUriName(getContext()));
    }

    /**
     * Called when the user selected a uri to write to.
     *
     * @param uri file to write to
     */
    private void onCreateDocument(@Nullable final Uri uri) {
        if (uri == null) {
            // nothing selected, just quit
            //noinspection ConstantConditions
            getActivity().finish();

        } else {
            mArchiveExportTask.startExport(uri);
        }
    }

    private void onExportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_error)
                    .setTitle(R.string.error_backup_failed)
                    .setMessage(createErrorReport(getContext(), message.result))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    @NonNull
    private String createErrorReport(@NonNull final Context context,
                                     @Nullable final Exception e) {
        String msg = null;

        if (e instanceof IOException) {
            // see if we can find the exact cause
            if (e.getCause() instanceof ErrnoException) {
                final int errno = ((ErrnoException) e.getCause()).errno;
                // write failed: ENOSPC (No space left on device)
                if (errno == OsConstants.ENOSPC) {
                    msg = context.getString(R.string.error_storage_no_space_left);
                } else {
                    // write to logfile for future reporting enhancements.
                    Logger.warn(context, TAG, "onExportFailed|errno=" + errno);
                }
            }

            // generic IOException message
            if (msg == null) {
                msg = StandardDialogs.createBadError(context, R.string.error_storage_not_writable);
            }
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = context.getString(R.string.error_unknown_long);
        }

        return msg;
    }

    private void onExportCancelled(@NonNull final FinishedMessage<Boolean> message) {
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
    private void onExportFinished(@NonNull final FinishedMessage<Boolean> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            final ExportHelper exportHelper = mExportViewModel.getExportHelper();

            //noinspection ConstantConditions
            final MaterialAlertDialogBuilder dialogBuilder =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_info)
                            .setTitle(R.string.progress_end_backup_success)
                            .setPositiveButton(R.string.done, (d, which) -> getActivity().finish());

            final Uri uri = exportHelper.getUri();
            final Pair<String, Long> uriInfo = FileUtils.getUriInfo(getContext(), uri);
            final String report = exportHelper.getResults().createReport(getContext(), uriInfo);
            if (exportHelper.offerEmail(uriInfo)) {
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

    public static class ResultContract
            extends ActivityResultContract<Void, Boolean> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @Nullable final Void aVoid) {
            return new Intent(context, HostingActivity.class)
                    .putExtra(HostingActivity.BKEY_FRAGMENT_TAG, ExportFragment.TAG);
        }

        @Override
        @NonNull
        public Boolean parseResult(final int resultCode,
                                   @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            return intent != null && resultCode == Activity.RESULT_OK;
        }
    }
}
