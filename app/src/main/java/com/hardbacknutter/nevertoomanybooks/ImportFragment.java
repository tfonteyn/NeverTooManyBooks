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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ArchiveContainer;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelperDialogFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportManager;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.FormattedMessageException;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ResultDataModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.ImportTaskModel;

/**
 * TODO: consider using true background tasks without progress dialog and have them report
 * being finished/failed via a notification. That would allow the user to continue using
 * the app while the export is running.
 */
public class ImportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ImportFragment";

    @Nullable
    private ProgressDialogFragment mProgressDialog;

    /** ViewModel. */
    private ResultDataModel mResultDataModel;

    /** Import. */
    private ImportTaskModel mImportModel;
    private final OptionsDialogBase.OptionsListener<ImportManager> mImportOptionsListener =
            new OptionsDialogBase.OptionsListener<ImportManager>() {
                @Override
                public void onOptionsSet(@NonNull final ImportManager options) {
                    mImportModel.startArchiveImportTask(options);
                }

                @Override
                public void onCancelled() {
                    //noinspection ConstantConditions
                    getActivity().finish();
                }
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import_export, container, false);
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (ImportHelperDialogFragment.TAG.equals(childFragment.getTag())) {
            ((ImportHelperDialogFragment) childFragment).setListener(mImportOptionsListener);
        }
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mResultDataModel = new ViewModelProvider(getActivity()).get(ResultDataModel.class);

        mImportModel = new ViewModelProvider(this).get(ImportTaskModel.class);
        mImportModel.onTaskProgress().observe(getViewLifecycleOwner(), this::onTaskProgress);
        mImportModel.onTaskFinished().observe(getViewLifecycleOwner(), this::onImportFinished);
        importPickUri();

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
            case UniqueId.REQ_IMPORT_PICK_URI: {
                // The user selected a file to import from. Next step asks for the options.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
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
            dialog = ProgressDialogFragment.newInstance(R.string.lbl_importing, false, true, 0);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCancellable(mImportModel.getTask());

        return dialog;
    }

    /**
     * Import Step 1: prompt the user for a uri to export to.
     */
    private void importPickUri() {
        // Import
        // This does not allow multiple saved files like "foo.tar (1)", "foo.tar (2)"
//        String[] mimeTypes = {"application/x-tar", "text/csv"};
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
//                .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                .setType("*/*");
        startActivityForResult(intent, UniqueId.REQ_IMPORT_PICK_URI);
    }

    /**
     * Import Step 2: show the options to the user.
     *
     * @param uri file to read from
     */
    private void importShowOptions(@NonNull final Uri uri) {
        // options will be overridden if the import is a CSV.
        ImportManager helper = new ImportManager(Options.ALL, uri);

        //noinspection ConstantConditions
        final ArchiveContainer container = helper.getContainer(getContext());
        if (!helper.isSupported(container)) {
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.error_cannot_import)
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
            return;
        }

        if (ArchiveContainer.CsvBooks.equals(container)) {
            // use more prudent default options for Csv files.
            helper.setOptions(Options.BOOKS | ImportManager.IMPORT_ONLY_NEW_OR_UPDATED);
            // Verify - this can be a dangerous operation
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setTitle(R.string.lbl_import_book_data)
                    .setMessage(R.string.warning_import_be_cautious)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                    .setPositiveButton(android.R.string.ok, (d, w) -> ImportHelperDialogFragment
                            .newInstance(helper)
                            .show(getChildFragmentManager(), ImportHelperDialogFragment.TAG))
                    .create()
                    .show();

        } else {
            // Show a quick-options dialog first.
            // The user can divert to the full options dialog if needed.
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.lbl_import)
                    .setMessage(R.string.txt_import_option_all_books)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> getActivity().finish())
                    .setNeutralButton(R.string.btn_options, (d, w) -> ImportHelperDialogFragment
                            .newInstance(helper)
                            .show(getChildFragmentManager(), ImportHelperDialogFragment.TAG))
                    .setPositiveButton(android.R.string.ok, (d, w) -> mImportModel
                            .startArchiveImportTask(helper))
                    .create()
                    .show();
        }
    }

    /**
     * Import finished/failed: Step 1: Process the result.
     *
     * @param message to process
     */
    private void onImportFinished(@NonNull final TaskListener.FinishMessage<ImportManager>
                                          message) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        switch (message.status) {
            case Success: {
                onImportFinished(R.string.progress_end_import_complete,
                                 message.result.getOptions(),
                                 message.result.getResults());
                break;
            }
            case Cancelled: {
                onImportFinished(R.string.progress_end_import_partially_complete,
                                 message.result.getOptions(),
                                 message.result.getResults());
                break;
            }
            case Failed: {
                onImportFailed(message.exception);
                break;
            }
        }
    }

    /**
     * Import finished: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports success or cancelled.
     * @param options what was actually imported
     * @param results what was imported
     */
    private void onImportFinished(@StringRes final int titleId,
                                  final int options,
                                  @NonNull final ImportResults results) {

        // Transform the result data into a user friendly report.
        final StringBuilder msg = new StringBuilder();

        //TODO: RTL
        if (results.booksCreated > 0 || results.booksUpdated > 0 || results.booksSkipped > 0) {
            msg.append("\n• ")
               .append(getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                 getString(R.string.lbl_books),
                                 results.booksCreated,
                                 results.booksUpdated,
                                 results.booksSkipped));
        }
        if (results.coversCreated > 0 || results.coversUpdated > 0 || results.coversSkipped > 0) {
            msg.append("\n• ")
               .append(getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                 getString(R.string.lbl_covers),
                                 results.coversCreated,
                                 results.coversUpdated,
                                 results.coversSkipped));
        }
        if (results.styles > 0) {
            msg.append("\n• ").append(getString(R.string.name_colon_value,
                                                getString(R.string.lbl_styles),
                                                String.valueOf(results.styles)));
        }
        if (results.preferences > 0) {
            msg.append("\n• ").append(getString(R.string.lbl_settings));
        }

        int failed = results.failedLinesNr.size();
        if (failed > 0) {
            final int fs;
            final Collection<String> msgList = new ArrayList<>();

            if (failed > 10) {
                // keep it sensible, list maximum 10 lines.
                failed = 10;
                fs = R.string.warning_import_csv_failed_lines_lots;
            } else {
                fs = R.string.warning_import_csv_failed_lines_some;
            }
            for (int i = 0; i < failed; i++) {
                msgList.add(getString(R.string.a_bracket_b_bracket,
                                      String.valueOf(results.failedLinesNr.get(i)),
                                      results.failedLinesMessage.get(i)));
            }

            //noinspection ConstantConditions
            msg.append("\n").append(getString(fs, Csv.textList(getContext(), msgList, null)));
        }

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(titleId)
                .setMessage(msg)
                .setPositiveButton(R.string.done, (d, w) -> {
                    mResultDataModel.putResultData(UniqueId.BKEY_IMPORT_RESULT, options);
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK,
                                            mResultDataModel.getResultData());
                    getActivity().finish();
                })
                .create()
                .show();
    }

    /**
     * Import failed: Step 2: Inform the user.
     *
     * @param e the Exception as returned from the import task
     */
    private void onImportFailed(@Nullable final Exception e) {
        String msg = null;

        if (e instanceof InvalidArchiveException) {
            msg = getString(R.string.error_import_invalid_archive);

        } else if (e instanceof IOException) {
            //ENHANCE: if (message.exception.getCause() instanceof ErrnoException) {
            //           int errno = ((ErrnoException) message.exception.getCause()).errno;
            msg = getString(R.string.error_storage_not_readable) + "\n\n"
                  + getString(R.string.error_if_the_problem_persists,
                              getString(R.string.lbl_send_debug_info));

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
                .setTitle(R.string.error_import_failed)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                .create()
                .show();
    }
}
