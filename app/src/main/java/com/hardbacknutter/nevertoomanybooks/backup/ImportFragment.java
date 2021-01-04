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
import android.system.Os;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReadMetaDataTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReaderTask;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.url.CalibreContentServerReader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImportBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.GeneralParsingException;

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
    protected ImportViewModel mVm;
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
    private FragmentImportBinding mVb;
    private ArchiveReadMetaDataTask mReadMetaDataTask;
    /**
     * The launcher for picking a Uri to read from.
     *
     * <a href="https://developer.android.com/guide/topics/providers/document-provider.html#client">
     * Android docs</a> : use a GetContent
     */
    private final ActivityResultLauncher<String> mOpenUriLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onOpenUri);
    private ArchiveReaderTask mArchiveReaderTask;
    @Nullable
    private ProgressDialogFragment mProgressDialog;

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
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm = new ViewModelProvider(getActivity()).get(ImportViewModel.class);
        mVm.init(getArguments());

        mReadMetaDataTask = new ViewModelProvider(this).get(ArchiveReadMetaDataTask.class);
        mReadMetaDataTask.onFinished().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        mReadMetaDataTask.onFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        mArchiveReaderTask = new ViewModelProvider(this).get(ArchiveReaderTask.class);
        mArchiveReaderTask.onProgressUpdate().observe(getViewLifecycleOwner(), this::onProgress);
        mArchiveReaderTask.onCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        mArchiveReaderTask.onFailure().observe(getViewLifecycleOwner(), this::onImportFailure);
        mArchiveReaderTask.onFinished().observe(getViewLifecycleOwner(), this::onImportFinished);

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        actionBar.setTitle(R.string.lbl_import);
        if (mVb.header == null) {
            // landscape layout only
            actionBar.setSubtitle(R.string.lbl_import_options);
        }

        if (!mVm.hasUri()) {
            // start the import process by asking the user for a Uri
            mOpenUriLauncher.launch(MIME_TYPES);
        } else {
            // or if we already have a uri when called,
            // or e.g. after a screen rotation, just show the screen/options again
            showScreen();
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
            mArchiveReaderTask.start(mVm.getImportHelper());
            return true;
        }
        return super.onOptionsItemSelected(item);
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

            } catch (@NonNull final InvalidArchiveException e) {
                onImportNotSupported(R.string.error_import_file_not_supported);
                return;
            } catch (@NonNull final FileNotFoundException e) {
                onImportNotSupported(R.string.error_import_file_not_recognized);
                return;
            }

            switch (helper.getEncoding()) {
                case Csv:
                    //URGENT: should make a backup before ANY csv import!
                    //noinspection ConstantConditions
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_warning)
                            .setTitle(R.string.lbl_import_book_data)
                            .setMessage(R.string.warning_import_csv)
                            .setNegativeButton(android.R.string.cancel,
                                               (d, w) -> getActivity().finish())
                            .setPositiveButton(android.R.string.ok, (d, w) -> showScreen())
                            .create()
                            .show();
                    break;

                case Zip:
                case Tar:
                case SqLiteDb:
                case Json:
                case CalibreCS:
                    showScreen();
                    break;

                case Xml:
                default:
                    onImportNotSupported(R.string.error_import_file_not_supported);
                    break;
            }
        }
    }

    /**
     * Update the screen with archive specific options and values.
     */
    private void showScreen() {
        final ImportHelper helper = mVm.getImportHelper();

        // Hide the content field, and start the metadata task which will provide the content
        mVb.archiveContent.setVisibility(View.INVISIBLE);
        mReadMetaDataTask.start(helper);

        //noinspection ConstantConditions
        mVb.archiveName.setText(helper.getUriInfo(getContext()).getDisplayName());

        final Set<RecordType> entries = helper.getImportEntries();

        mVb.cbxBooks.setChecked(entries.contains(RecordType.Books));
        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setImportEntry(RecordType.Books, isChecked);
            mVb.rbBooksGroup.setEnabled(isChecked);
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

        // Set the visibility depending on the encoding
        switch (helper.getEncoding()) {
            case Zip:
            case Tar: {
                // all options available
                mVb.cbxBooks.setVisibility(View.VISIBLE);
                mVb.cbxCovers.setVisibility(View.VISIBLE);
                mVb.cbxPrefs.setVisibility(View.VISIBLE);
                mVb.cbxStyles.setVisibility(View.VISIBLE);
                break;
            }
            case Json: {
                // all options, except covers
                mVb.cbxBooks.setVisibility(View.VISIBLE);
                mVb.cbxCovers.setVisibility(View.GONE);
                mVb.cbxPrefs.setVisibility(View.VISIBLE);
                mVb.cbxStyles.setVisibility(View.VISIBLE);
                break;
            }
            case Csv:
            case SqLiteDb: {
                // show only the book options
                mVb.cbxBooks.setVisibility(View.GONE);
                mVb.cbxCovers.setVisibility(View.GONE);
                mVb.cbxPrefs.setVisibility(View.GONE);
                mVb.cbxStyles.setVisibility(View.GONE);
                break;
            }
            case CalibreCS:
                mVb.cbxBooks.setEnabled(false);
                mVb.cbxBooks.setVisibility(View.VISIBLE);
                mVb.cbxCovers.setVisibility(View.VISIBLE);
                mVb.cbxStyles.setVisibility(View.GONE);
                mVb.cbxPrefs.setVisibility(View.GONE);
                break;

            case Xml:
                // shouldn't even get here
                onImportNotSupported(R.string.error_import_file_not_supported);
                break;
        }

        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void onMetaDataRead(@NonNull final FinishedMessage<ArchiveMetaData> message) {
        final ArchiveMetaData archiveMetaData = message.result;
        if (archiveMetaData != null) {
            final StringJoiner info = new StringJoiner("\n");

            // If this is a Calibre import, show the library name
            final String calibreLibrary = archiveMetaData.getBundle().getString(
                    CalibreContentServerReader.ARCH_MD_DEFAULT_LIBRARY);
            if (calibreLibrary != null && !calibreLibrary.isEmpty()) {
                info.add(calibreLibrary);
            }

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
                mVb.archiveContent.setVisibility(View.INVISIBLE);
            }
        } else {
            mVb.archiveContent.setVisibility(View.INVISIBLE);
        }
    }

    private void onImportNotSupported(@StringRes final int stringResId) {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_error)
                .setMessage(stringResId)
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
     * Import finished: Step 1: Process the message.
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
     * Import finished/cancelled: Step 2: Inform the user.
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

        } else if (e instanceof GeneralSecurityException) {
            // There was something wrong with certificates/key on OUR end
            // CertificateException, NoSuchAlgorithmException,
            // KeyStoreException, KeyManagementException
            //TODO: give user detailed message
            msg = getString(R.string.httpErrorFailedSslHandshake);

        } else if (e instanceof SSLException) {
            // There was something wrong with certificates/key on the REMOTE end
            //TODO: give user detailed message
            msg = getString(R.string.httpErrorFailedSslHandshake);

        } else if (e instanceof ImportException) {
            msg = e.getLocalizedMessage();

        } else if (e instanceof GeneralParsingException) {
            // Either the remote end gave us garbage, or the data format changed
            // and we failed to decode it
            msg = getString(R.string.error_network_site_has_problems);
            //noinspection ConstantConditions
            Logger.error(getContext(), TAG, e, msg);

        } else if (e instanceof FileNotFoundException) {
            msg = getString(R.string.httpErrorFile);

        } else if (e instanceof IOException) {
            // catch all, potentially look at cause.
            final Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof ErrnoException) {
                    //TODO: give user detailed message
                    final int errno = ((ErrnoException) cause).errno;
                    msg = Os.strerror(errno);
                } else if (cause instanceof SQLException) {
                    //TODO: give user detailed message
                    msg = getString(R.string.error_unknown_long);
                }
            }

            if (msg == null) {
                //noinspection ConstantConditions
                msg = StandardDialogs
                        .createBadError(getContext(), R.string.error_storage_not_readable);
            }
        }

        // generic unknown message
        if (msg == null || msg.isEmpty()) {
            msg = getString(R.string.error_unknown_long);
        }

        return msg;
    }

    protected void onProgress(@NonNull final ProgressMessage message) {
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
}
