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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.HostingActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImportBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * This fragment is a blank screen and all actions are done using dialogs (fullscreen and actual).
 */
public class ImportFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "ImportFragment";
    /**
     * The mime types accepted for importing files SHOULD be set to this list:
     * "application/zip", "application/x-tar", "text/csv", "application/x-sqlite3"
     * <p>
     * As it turns out the mime types are not an Android feature, but support for them
     * is specific to whatever application is responding to {@link Intent#ACTION_GET_CONTENT}
     * which in practice is extremely limited.
     * e.g. "text/*" does not even allow csv files in the standard Android 8.0 Files app.
     * <p>
     * So we have no choice but to accept simply all files and deal with invalid ones later.
     * Also see {@link #mOpenUriLauncher}.
     */
    private static final String MIME_TYPES = "*/*";

    /**
     * The ViewModel and the {@link #mArchiveReaderTask} could be folded into one object,
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
    private ArchiveReaderTask mArchiveReaderTask;
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    /** View Binding. */
    private FragmentImportBinding mVb;
    /**
     * The launcher for picking a Uri to read from.
     *
     * <a href="https://developer.android.com/guide/topics/providers/document-provider.html#client">
     * Android docs</a> : use a GetContent
     */
    private final ActivityResultLauncher<String> mOpenUriLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onOpenUri);

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentImportBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        actionBar.setTitle(R.string.lbl_import);
        if (mVb.header == null) {
            // landscape layout only
            actionBar.setSubtitle(R.string.lbl_import_options);
        }

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm = new ViewModelProvider(getActivity()).get(ImportViewModel.class);

        mArchiveReaderTask = new ViewModelProvider(this).get(ArchiveReaderTask.class);
        mArchiveReaderTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mArchiveReaderTask.onCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        mArchiveReaderTask.onFailure().observe(getViewLifecycleOwner(), this::onImportFailure);
        mArchiveReaderTask.onFinished().observe(getViewLifecycleOwner(), this::onImportFinished);


        if (!mVm.hasUri()) {
            // start the import process by asking the user for a Uri
            mOpenUriLauncher.launch(MIME_TYPES);
        } else {
            // or e.g. after a screen rotation, just show the screen/options again
            showScreen();
        }
    }

    /**
     * Called when the user selected a uri to read from.
     * Prepares the options suited for the selected import file.
     *
     * @param uri file to read from
     */
    private void onOpenUri(@Nullable final Uri uri) {
        if (uri == null) {
            // nothing selected, just quit
            //noinspection ConstantConditions
            getActivity().finish();

        } else {
            final ImportHelper helper;
            try {
                //noinspection ConstantConditions
                helper = mVm.createImportHelper(getContext(), uri);
            } catch (@NonNull final IOException | InvalidArchiveException e) {
                onImportNotSupported();
                return;
            }

            final ArchiveEncoding archiveEncoding = helper.getArchiveEncoding();
            switch (archiveEncoding) {
                case Csv:
                    // Default: new books and sync updates
                    helper.setImportEntry(RecordType.Books, true);
                    helper.setUpdatesMustSync();

                    //URGENT: should make a backup before ANY csv import!
                    //noinspection ConstantConditions
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_warning)
                            .setTitle(R.string.lbl_import_book_data)
                            .setMessage(R.string.warning_import_be_cautious)
                            .setNegativeButton(android.R.string.cancel,
                                               (d, w) -> getActivity().finish())
                            .setPositiveButton(android.R.string.ok, (d, w) -> showScreen())
                            .create()
                            .show();
                    break;

                case Zip:
                case Tar:
                    // Default: update all entries and sync updates
                    helper.setImportEntry(RecordType.Styles, true);
                    helper.setImportEntry(RecordType.Preferences, true);
                    helper.setImportEntry(RecordType.Books, true);
                    helper.setImportEntry(RecordType.Cover, true);
                    helper.setUpdatesMustSync();
                    showScreen();
                    break;

                case SqLiteDb:
                    // Default: new books only
                    helper.setImportEntry(RecordType.Books, true);
                    helper.setSkipUpdates();
                    showScreen();
                    break;

                case Json:
                    helper.setImportEntry(RecordType.Styles, true);
                    helper.setImportEntry(RecordType.Preferences, true);
                    helper.setImportEntry(RecordType.Books, true);
                    helper.setUpdatesMustSync();
                    showScreen();
                    break;

                case Xml:
                    // we can import Styles and Preferences from xml, but there is no point.
                default:
                    onImportNotSupported();
                    break;
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_import, menu);

        final MenuItem menuItem = menu.findItem(R.id.MENU_ACTION_CONFIRM);
        final Button button = menuItem.getActionView().findViewById(R.id.btn_confirm);
        button.setText(menuItem.getTitle());
        button.setOnClickListener(v -> onOptionsItemSelected(menuItem));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            mArchiveReaderTask.startImport(mVm.getImportHelper());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Update the screen with archive specific options and values.
     */
    private void showScreen() {
        final ImportHelper helper = mVm.getImportHelper();

        //noinspection ConstantConditions
        final String displayName = helper.getArchiveName(getContext());
        mVb.archiveName.setText(displayName);

        final ArchiveMetaData archiveMetaData = helper.getArchiveMetaData();
        if (archiveMetaData != null) {
            final StringJoiner info = new StringJoiner("\n");

            final int bookCount = archiveMetaData.getBookCount();
            if (bookCount > 0) {
                info.add(getString(R.string.name_colon_value,
                                   getString(R.string.lbl_books), String.valueOf(bookCount)));
            }
            final int coverCount = archiveMetaData.getCoverCount();
            if (coverCount > 0) {
                info.add(getString(R.string.name_colon_value,
                                   getString(R.string.lbl_covers), String.valueOf(coverCount)));
            }
            if (info.length() > 0) {
                mVb.archiveContent.setText(info.toString());
                mVb.archiveContent.setVisibility(View.VISIBLE);
            } else {
                mVb.archiveContent.setVisibility(View.GONE);
            }
        } else {
            mVb.archiveContent.setVisibility(View.GONE);
        }

        final Set<RecordType> entries = helper.getImportEntries();

        mVb.cbxBooks.setChecked(entries.contains(RecordType.Books));
        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setImportEntry(RecordType.Books, isChecked);
            mVb.rbBooksGroup.setEnabled(isChecked);
        });

        mVb.rbUpdatedBooksSkip.setChecked(helper.isSkipUpdates());
        mVb.rbUpdatedBooksSkipInfo.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbUpdatedBooksOverwrite.setChecked(helper.isUpdatesMayOverwrite());
        mVb.rbUpdatedBooksOverwriteInfo.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbUpdatedBooksSync.setChecked(helper.isUpdatesMustSync());
        mVb.rbUpdatedBooksSyncInfo.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == mVb.rbUpdatedBooksSkip.getId()) {
                helper.setSkipUpdates();

            } else if (checkedId == mVb.rbUpdatedBooksOverwrite.getId()) {
                helper.setUpdatesMayOverwrite();

            } else if (checkedId == mVb.rbUpdatedBooksSync.getId()) {
                helper.setUpdatesMustSync();
            }
        });

        mVb.cbxCovers.setChecked(entries.contains(RecordType.Cover));
        mVb.cbxCovers.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                .setImportEntry(RecordType.Cover, isChecked));

        mVb.cbxStyles.setChecked(entries.contains(RecordType.Styles));
        mVb.cbxStyles.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                .setImportEntry(RecordType.Styles, isChecked));

        mVb.cbxPrefs.setChecked(entries.contains(RecordType.Preferences));
        mVb.cbxPrefs.setOnCheckedChangeListener((buttonView, isChecked) -> helper
                .setImportEntry(RecordType.Preferences, isChecked));

        switch (helper.getArchiveEncoding()) {
            case Zip:
            case Tar: {
                // all options available
                mVb.cbxGroup.setVisibility(View.VISIBLE);
                break;
            }
            case Json: {
                // all options, except covers
                mVb.cbxGroup.setVisibility(View.VISIBLE);
                mVb.cbxCovers.setVisibility(View.GONE);
                break;
            }
            case Csv:
            case SqLiteDb: {
                // show only the book options
                mVb.cbxGroup.setVisibility(View.GONE);
                break;
            }

            case Xml:
                // shouldn't even get here
                onImportNotSupported();
                break;
        }

        mVb.getRoot().setVisibility(View.VISIBLE);
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
        dialog.setCanceller(mArchiveReaderTask);

        return dialog;
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    private void onImportNotSupported() {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_error)
                .setMessage(R.string.error_import_file_not_supported)
                .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                .create()
                .show();
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

    private void onImportCancelled(@NonNull final FinishedMessage<ImportResults> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            if (message.result != null) {
                onImportFinished(R.string.progress_end_import_partially_complete, message.result);
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
    private void onImportFinished(@NonNull final FinishedMessage<ImportResults> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            onImportFinished(R.string.progress_end_import_complete, message.result);
        }
    }

    /**
     * Import finished: Step 2: Inform the user.
     *
     * @param titleId for the dialog title; reports success or cancelled.
     * @param result  of the import
     */
    private void onImportFinished(@StringRes final int titleId,
                                  @NonNull final ImportResults result) {

        if (result.styles > 0) {
            //noinspection ConstantConditions
            StyleDAO.updateMenuOrder(getContext());
        }

        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_info)
                .setTitle(titleId)
                .setMessage(createReport(result))
                .setPositiveButton(R.string.done, (d, w) -> {
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
    private String createReport(@NonNull final ImportResults result) {

        final Context context = getContext();
        final StringJoiner report = new StringJoiner("\n");
        report.setEmptyValue("");

        //TODO: RTL
        if (result.booksCreated > 0 || result.booksUpdated > 0 || result.booksSkipped > 0) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                         context.getString(R.string.lbl_books),
                                         result.booksCreated,
                                         result.booksUpdated,
                                         result.booksSkipped));
        }
        if (result.coversCreated > 0 || result.coversUpdated > 0 || result.coversSkipped > 0) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.progress_msg_x_created_y_updated_z_skipped,
                                         context.getString(R.string.lbl_covers),
                                         result.coversCreated,
                                         result.coversUpdated,
                                         result.coversSkipped));
        }
        if (result.styles > 0) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.name_colon_value,
                                         context.getString(R.string.lbl_styles),
                                         String.valueOf(result.styles)));
        }
        if (result.preferences > 0) {
            //noinspection ConstantConditions
            report.add(context.getString(R.string.lbl_settings));
        }

        int failed = result.failedLinesNr.size();
        if (failed == 0) {
            return report.toString();
        }

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
            //noinspection ConstantConditions
            msgList.add(context.getString(R.string.a_bracket_b_bracket,
                                          String.valueOf(result.failedLinesNr.get(i)),
                                          result.failedLinesMessage.get(i)));
        }

        return report.toString() + "\n" + context.getString(
                fs, msgList.stream()
                           .map(s -> context.getString(R.string.list_element, s))
                           .collect(Collectors.joining("\n")));
    }

    /**
     * Transform the failure into a user friendly report.
     *
     * @param e error exception
     *
     * @return report string
     */
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

    public static class ResultContract
            extends ActivityResultContract<Void, ImportResults> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @Nullable final Void aVoid) {
            return new Intent(context, HostingActivity.class)
                    .putExtra(HostingActivity.BKEY_FRAGMENT_TAG, ImportFragment.TAG);
        }

        @Override
        @Nullable
        public ImportResults parseResult(final int resultCode,
                                         @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return null;
            }
            return intent.getParcelableExtra(ImportResults.BKEY_IMPORT_RESULTS);
        }
    }
}
