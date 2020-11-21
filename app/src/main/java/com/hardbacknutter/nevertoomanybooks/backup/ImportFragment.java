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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.HostingActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveContainer;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveImportTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

public class ImportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ImportFragment";
    /** FragmentResultListener request key. */
    private static final String RK_IMPORT_OPTIONS = TAG + ":rk:" + ImportOptionsDialogFragment.TAG;
    /**
     * The mime types accepted for importing files.
     * This is the list how it SHOULD be set...
     * {"application/zip",
     * "application/x-tar",
     * "text/csv",
     * "application/x-sqlite3"};
     * <p>
     * As it turns out the mime types are not an Android feature, but support for them
     * is specific to whatever application is responding to {@link Intent#ACTION_OPEN_DOCUMENT}
     * which in practice is extremely limited.
     * e.g. "text/*" does not even allow csv files in the standard Files app.
     * So we have no choice but to accept simply all files and deal with invalid ones later.
     */
    private static final String[] MIME_TYPES = {"*/*"};

    /**
     * The ViewModel and the {@link #mArchiveImportTask} could be folded into one object,
     * but we're trying to keep task logic separate for now.
     */
    private ImportViewModel mVm;
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
    private ArchiveImportTask mArchiveImportTask;
    private final ImportOptionsDialogFragment.Launcher mImportOptionsLauncher =
            new ImportOptionsDialogFragment.Launcher() {
                @Override
                public void onResult(final boolean startTask) {
                    if (startTask) {
                        mArchiveImportTask.startImport(mVm.getImportHelper());
                    } else {
                        //noinspection ConstantConditions
                        getActivity().finish();
                    }
                }
            };
    /** The launcher for picking a Uri. */
    private final ActivityResultLauncher<String[]> mOpenDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                                      this::onOpenDocument);
    @Nullable
    private ProgressDialogFragment mProgressDialog;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mImportOptionsLauncher.register(this, RK_IMPORT_OPTIONS);
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

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm = new ViewModelProvider(getActivity()).get(ImportViewModel.class);

        mArchiveImportTask = new ViewModelProvider(this).get(ArchiveImportTask.class);
        mArchiveImportTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mArchiveImportTask.onCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        mArchiveImportTask.onFailure().observe(getViewLifecycleOwner(), this::onImportFailure);
        mArchiveImportTask.onFinished().observe(getViewLifecycleOwner(), this::onImportFinished);

        // If the task is NOT already running (e.g. after a screen rotation...)
        // then start the import process.
        if (!mArchiveImportTask.isRunning()) {
            mOpenDocumentLauncher.launch(MIME_TYPES);
        }
    }

    /**
     * Called when the user selected a uri to read from.
     * Shows the import options; which in turn allow to start the import process.
     *
     * @param uri file to read from
     */
    private void onOpenDocument(@Nullable final Uri uri) {
        if (uri == null) {
            // nothing selected, just quit
            //noinspection ConstantConditions
            getActivity().finish();

        } else {
            final ImportHelper importHelper = mVm.createImportManager(uri);

            //noinspection ConstantConditions
            if (!importHelper.isSupported(getContext())) {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_error)
                        .setMessage(R.string.error_import_file_not_supported)
                        .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                        .create()
                        .show();
                return;
            }

            final ArchiveContainer container = importHelper.getContainer(getContext());
            //noinspection EnumSwitchStatementWhichMissesCases
            switch (container) {
                case CsvBooks:
                    importHelper.setOptions(
                            ImportHelper.Options.BOOKS | ImportHelper.Options.UPDATED_BOOKS_SYNC);

                    //URGENT: make a backup before ANY csv import!
                    //noinspection ConstantConditions
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_warning)
                            .setTitle(R.string.lbl_import_book_data)
                            .setMessage(R.string.warning_import_be_cautious)
                            .setNegativeButton(android.R.string.cancel,
                                               (d, w) -> getActivity().finish())
                            .setPositiveButton(android.R.string.ok, (d, w) ->
                                    mImportOptionsLauncher.launch())
                            .create()
                            .show();

                    break;

                case Zip:
                case Tar:
                case SqLiteDb:
                    importHelper.setOptions(
                            ImportHelper.Options.ENTITIES
                            | ImportHelper.Options.UPDATED_BOOKS_SYNC);
                    mImportOptionsLauncher.launch();
                    break;

                default:
                    throw new IllegalArgumentException(String.valueOf(container));
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
                    getString(R.string.lbl_importing), false, true);
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        dialog.setCanceller(mArchiveImportTask);

        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
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
                    .setMessage(createErrorReport(message.result))
                    .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                    .create()
                    .show();
        }
    }

    @NonNull
    private String createErrorReport(@Nullable final Exception e) {
        String msg = null;

        if (e instanceof InvalidArchiveException) {
            msg = getString(R.string.error_import_file_not_supported);

        } else if (e instanceof ImportException) {
            msg = e.getLocalizedMessage();

        } else if (e instanceof IOException) {
            //ENHANCE: if (message.exception.getCause() instanceof ErrnoException) {
            //           int errno = ((ErrnoException) message.exception.getCause()).errno;
            //noinspection ConstantConditions
            msg = StandardDialogs.createBadError(getContext(), R.string.error_storage_not_readable);
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = getString(R.string.error_unknown_long);
        }

        return msg;
    }

    private void onImportCancelled(@NonNull final FinishedMessage<Boolean> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            if (message.result != null) {
                // message.result is always 'true'
                onImportFinished(R.string.progress_end_import_partially_complete);
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
    private void onImportFinished(@NonNull final FinishedMessage<Boolean> message) {
        closeProgressDialog();
        if (message.isNewEvent()) {
            onImportFinished(R.string.progress_end_import_complete);
        }
    }

    /**
     * Import finished: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports success or cancelled.
     */
    private void onImportFinished(@StringRes final int titleId) {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_info)
                .setTitle(titleId)
                .setMessage(mVm.getImportHelper().getResults()
                               .createReport(getContext()))
                .setPositiveButton(R.string.done, (d, w) -> {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mVm.onImportFinished());
                    getActivity().finish();
                })
                .create()
                .show();
    }

    public static class ResultContract
            extends ActivityResultContract<Void, Integer> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @Nullable final Void aVoid) {
            return new Intent(context, HostingActivity.class)
                    .putExtra(HostingActivity.BKEY_FRAGMENT_TAG, ImportFragment.TAG);
        }

        @Override
        @NonNull
        public Integer parseResult(final int resultCode,
                                   @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return ImportHelper.Options.NOTHING;
            }
            return intent
                    .getIntExtra(ImportResults.BKEY_IMPORT_RESULTS, ImportHelper.Options.NOTHING);
        }
    }
}
