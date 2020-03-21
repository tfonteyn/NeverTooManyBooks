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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ExportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.FormattedMessageException;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import_export, container, false);
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (ExportHelperDialogFragment.TAG.equals(childFragment.getTag())) {
            ((ExportHelperDialogFragment) childFragment).setListener(mExportOptionsListener);
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        mExportModel = new ViewModelProvider(this).get(ExportTaskModel.class);
        mExportModel.onTaskProgress().observe(getViewLifecycleOwner(), this::onTaskProgress);
        mExportModel.onTaskFinished().observe(getViewLifecycleOwner(), this::onExportFinished);

        exportShowOptions();
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
            case UniqueId.REQ_EXPORT_PICK_URI: {
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
            dialog = ProgressDialogFragment.newInstance(R.string.title_backing_up, false, true, 0);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCancellable(mExportModel.getTask());
        return dialog;
    }

    /**
     * Export Step 1: show the options to the user.
     */
    private void exportShowOptions() {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.info_export_backup_all)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    //noinspection ConstantConditions
                    getActivity().finish();
                })
                .setNeutralButton(R.string.btn_options, (dialog, which)
                        -> ExportHelperDialogFragment.newInstance()
                                                     .show(getChildFragmentManager(),
                                                           ExportHelperDialogFragment.TAG))
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
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
                .putExtra(Intent.EXTRA_TITLE, mExportModel.getDefaultUriName((getContext())));
        startActivityForResult(intent, UniqueId.REQ_EXPORT_PICK_URI);
    }

    /**
     * Export finished/failed: Step 1: Process the result.
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
                onExportFinished(message.result.getResults(),
                                 message.result.getUri());
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
                onExportFailed(message.exception);
                break;
            }
        }
    }

    /**
     * Export finished: Step 2: Inform the user.
     */
    private void onExportFinished(@NonNull final ExportResults results,
                                  @NonNull final Uri uri) {
        // Transform the result data into a user friendly report.
        final StringBuilder msg = new StringBuilder();

        //TODO: LTR
        // slightly misleading. The text currently says "processed" but it's really "exported".
        if (results.booksExported > 0) {
            msg.append("\n• ")
               .append(getString(R.string.info_export_result_n_books_processed,
                                 results.booksExported));
        }
        if (results.coversExported > 0
            || results.coversMissing[0] > 0
            || results.coversMissing[1] > 0) {
            msg.append("\n• ")
               .append(getString(R.string.info_export_result_n_covers_processed_m_missing,
                                 results.coversExported,
                                 results.coversMissing[0],
                                 results.coversMissing[1]));
        }

        if (results.styles > 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if (results.preferences > 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }

        //noinspection ConstantConditions
        final Pair<String, Long> uriInfo = FileUtils.getUriInfo(getContext(), uri);
        // The below works, but we cannot get the folder name for the file.
        // Disabling for now. We'd need to change the descriptive string not to include the folder.
        if (uriInfo != null && uriInfo.first != null && uriInfo.second != null) {
            msg.append("\n\n")
               .append(getString(R.string.X_export_info_success_archive_details,
                                 "",
                                 uriInfo.first,
                                 FileUtils.formatFileSize(getContext(), uriInfo.second)));
        }

        final long fileSize;
        if (uriInfo != null && uriInfo.second != null) {
            fileSize = uriInfo.second;
        } else {
            fileSize = 0;
        }
        // up to 5mb
        boolean offerEmail = fileSize > 0 && fileSize < 5_000_000;

        if (offerEmail) {
            msg.append("\n\n").append(getString(R.string.confirm_email_export));
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.progress_end_backup_success)
                .setMessage(msg)
                .setPositiveButton(R.string.done, (d, which) -> {
                    //noinspection ConstantConditions
                    getActivity().finish();
                })
                .create();

        if (offerEmail) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.btn_email),
                             (d, which) -> onExportEmail(uri));
        }

        dialog.show();

        //noinspection ConstantConditions
        getActivity().setResult(Activity.RESULT_OK, mResultDataModel.getResultData());
    }

    /**
     * Export failed: Step 2: Inform the user.
     *
     * @param e the Exception as returned from the export task
     */
    private void onExportFailed(@Nullable final Exception e) {
        String msg = null;

        if (e instanceof IOException) {
            // see if we can find the exact cause
            if (e.getCause() instanceof ErrnoException) {
                final int errno = ((ErrnoException) e.getCause()).errno;
                // write failed: ENOSPC (No space left on device)
                if (errno == OsConstants.ENOSPC) {
                    msg = getString(R.string.error_storage_no_space_left);
                } else {
                    // write to logfile for future reporting enhancements.
                    //noinspection ConstantConditions
                    Logger.warn(getContext(), TAG, "onExportFailed|errno=" + errno);
                }
            }

            // generic IOException message
            if (msg == null) {
                msg = getString(R.string.error_storage_not_writable) + "\n\n"
                      + getString(R.string.error_if_the_problem_persists,
                                  getString(R.string.lbl_send_debug_info));
            }
        } else if (e instanceof FormattedMessageException) {
            //noinspection ConstantConditions
            msg = ((FormattedMessageException) e).getLocalizedMessage(getContext());
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = getString(R.string.error_unexpected_error);
        }

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.error_backup_failed)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    //noinspection ConstantConditions
                    getActivity().finish();
                })
                .create()
                .show();
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
            startActivity(Intent.createChooser(intent, getString(R.string.title_send_mail)));
            //noinspection ConstantConditions
            getActivity().finish();

        } catch (@NonNull final NullPointerException e) {
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e);
            new MaterialAlertDialogBuilder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.error_email_failed)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        //noinspection ConstantConditions
                        getActivity().finish();
                    })
                    .create()
                    .show();
        }
    }
}
