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
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentImportBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class ImportFragment
        extends BaseFragment {

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

    /** The ViewModel. */
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

    /**
     * The launcher for picking a Uri to read from.
     *
     * <a href="https://developer.android.com/guide/topics/providers/document-provider.html#client">
     * Android docs</a> : use a GetContent
     */
    private final ActivityResultLauncher<String> mOpenUriLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onOpenUri);

    @Nullable
    private ProgressDelegate mProgressDelegate;
    /** Ref to the actual Toolbar so we can enable/disable its menu. */
    private Toolbar mToolbar;

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
        setTitle(R.string.lbl_import);

        //noinspection ConstantConditions
        mToolbar = getActivity().findViewById(R.id.toolbar);

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm = new ViewModelProvider(getActivity()).get(ImportViewModel.class);
        mVm.init(getArguments());

        mVm.onMetaDataRead().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        mVm.onMetaDataFailure().observe(getViewLifecycleOwner(), this::onImportFailure);

        mVm.onProgress().observe(getViewLifecycleOwner(), this::onProgress);
        mVm.onImportCancelled().observe(getViewLifecycleOwner(), this::onImportCancelled);
        mVm.onImportFailure().observe(getViewLifecycleOwner(), this::onImportFailure);
        mVm.onImportFinished().observe(getViewLifecycleOwner(), this::onImportFinished);

        if (mVm.hasUri()) {
            // if we already have a uri when called,
            // or e.g. after a screen rotation, just show the screen/options again
            showOptions();
        } else {
            // start the import process by asking the user for a Uri
            mOpenUriLauncher.launch(MIME_TYPES);
        }
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

        final MenuItem menuItem = mToolbar.getMenu().findItem(R.id.MENU_ACTION_CONFIRM);
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
                onImportNotSupported(R.string.error_file_not_recognized);
                return;
            }

            switch (helper.getEncoding()) {
                case Csv:
                    // CsvArchiveReader will make a database backup before importing.
                    //noinspection ConstantConditions
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_baseline_warning_24)
                            .setTitle(R.string.lbl_import_books)
                            .setMessage(R.string.warning_import_csv)
                            .setNegativeButton(android.R.string.cancel,
                                               (d, w) -> getActivity().finish())
                            .setPositiveButton(android.R.string.ok, (d, w) -> showOptions())
                            .create()
                            .show();
                    break;

                case Zip:
                case Tar:
                case SqLiteDb:
                case Json:
                case CalibreCS:
                    showOptions();
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
    private void showOptions() {
        showArchiveDetails();
        if (mVm.getArchiveMetaData() == null) {
            mVm.readMetaData();
        }

        final ImportHelper helper = mVm.getImportHelper();

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
        mVb.cbxPrefs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setImportEntry(RecordType.Preferences, isChecked);
            helper.setImportEntry(RecordType.Certificates, isChecked);
        });


        mVb.rbImportBooksOptionNewOnly.setChecked(helper.isNewBooksOnly());
        mVb.infImportBooksOptionNewOnly.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbImportBooksOptionNewAndUpdated.setChecked(helper.isNewAndUpdatedBooks());
        mVb.infImportBooksOptionNewAndUpdated.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbImportBooksOptionAll.setChecked(helper.isAllBooks());
        mVb.infImportBooksOptionAll.setOnClickListener(StandardDialogs::infoPopup);

        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == mVb.rbImportBooksOptionNewOnly.getId()) {
                helper.setNewBooksOnly();

            } else if (checkedId == mVb.rbImportBooksOptionNewAndUpdated.getId()) {
                helper.setNewAndUpdatedBooks();

            } else if (checkedId == mVb.rbImportBooksOptionAll.getId()) {
                helper.setAllBooks();
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
            case CalibreCS: {
                mVb.cbxBooks.setEnabled(false);
                mVb.cbxBooks.setVisibility(View.VISIBLE);
                mVb.cbxCovers.setVisibility(View.VISIBLE);
                mVb.cbxStyles.setVisibility(View.GONE);
                mVb.cbxPrefs.setVisibility(View.GONE);
                break;
            }
            case Xml: {
                // shouldn't even get here
                onImportNotSupported(R.string.error_import_file_not_supported);
                break;
            }
        }

        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void onMetaDataRead(@NonNull final FinishedMessage<ArchiveMetaData> message) {
        if (message.isNewEvent()) {
            mVm.setArchiveMetaData(message.result);
            showArchiveDetails();
        }
    }

    /**
     * Display the name of the archive + any valid data we can get from the archive.
     * <p>
     * All visibility for the archiveContent + calibreLibrary is handled here.
     */
    private void showArchiveDetails() {
        final ImportHelper helper = mVm.getImportHelper();

        //noinspection ConstantConditions
        mVb.archiveName.setText(helper.getUriInfo(getContext()).getDisplayName(getContext()));

        final ArchiveMetaData metaData = mVm.getArchiveMetaData();
        if (metaData != null) {
            // got data, we'll fill this field, SHOW it
            mVb.archiveContent.setVisibility(View.VISIBLE);

            if (helper.getEncoding() == ArchiveEncoding.CalibreCS) {
                mVb.lblCalibreLibrary.setVisibility(View.VISIBLE);
                showCalibreMetaData(metaData);
            } else {
                mVb.lblCalibreLibrary.setVisibility(View.GONE);
                showArchiveMetaData(metaData);
            }
        } else {
            // no metadata at all, REMOVE the calibre field, HIDE the content field
            mVb.lblCalibreLibrary.setVisibility(View.GONE);
            mVb.archiveContent.setVisibility(View.INVISIBLE);
        }
    }

    private void showArchiveMetaData(@NonNull final ArchiveMetaData metaData) {
        // some stats of what's inside the archive
        final StringJoiner archiveContent = new StringJoiner("\n");

        final LocalDateTime creationDate = metaData.getCreatedLocalDate();

        if (creationDate != null) {
            //noinspection ConstantConditions
            archiveContent.add(getString(R.string.name_colon_value,
                                         getString(R.string.lbl_created),
                                         DateUtils.toDisplay(getContext(), creationDate)));
        }
        if (metaData.hasBookCount()) {
            archiveContent.add(getString(R.string.name_colon_value,
                                         getString(R.string.lbl_books),
                                         String.valueOf(metaData.getBookCount())));
        }

        if (metaData.hasCoverCount()) {
            archiveContent.add(getString(R.string.name_colon_value,
                                         getString(R.string.lbl_covers),
                                         String.valueOf(metaData.getCoverCount())));
        }
        mVb.archiveContent.setText(archiveContent.toString());
    }

    private void showCalibreMetaData(@NonNull final ArchiveMetaData metaData) {

        final ArrayList<CalibreLibrary> libraries = metaData
                .getBundle().getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST);

        //noinspection ConstantConditions
        if (libraries.size() == 1) {
            onCalibreLibrarySelected(libraries.get(0));

        } else {
            //noinspection ConstantConditions
            final ExtArrayAdapter<CalibreLibrary> adapter =
                    new EntityArrayAdapter<>(getContext(), libraries);

            mVb.calibreLibrary.setAdapter(adapter);
            mVb.calibreLibrary.setOnItemClickListener(
                    (av, v, position, id) -> onCalibreLibrarySelected(libraries.get(position)));

            @Nullable
            final CalibreLibrary selectedLibrary = mVm
                    .getImportHelper()
                    .getExtraArgs()
                    .getParcelable(CalibreContentServer.BKEY_LIBRARY);
            if (selectedLibrary != null) {
                onCalibreLibrarySelected(selectedLibrary);
            } else {
                final CalibreLibrary defaultLibrary = metaData
                        .getBundle().getParcelable(CalibreContentServer.BKEY_LIBRARY);
                //noinspection ConstantConditions
                onCalibreLibrarySelected(defaultLibrary);
            }
        }
    }

    private void onCalibreLibrarySelected(@NonNull final CalibreLibrary library) {
        mVb.calibreLibrary.setText(library.getName(), false);

        mVb.archiveContent.setText(getString(R.string.name_colon_value,
                                             getString(R.string.lbl_books),
                                             String.valueOf(library.getTotalBooks())));
        mVm.getImportHelper()
           .getExtraArgs()
           .putParcelable(CalibreContentServer.BKEY_LIBRARY, library);
    }

    private void onImportNotSupported(@StringRes final int stringResId) {
        //noinspection ConstantConditions
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_baseline_error_24)
                .setMessage(stringResId)
                .setPositiveButton(android.R.string.ok, (d, w) -> getActivity().finish())
                .create()
                .show();
    }

    private void onImportFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, message.result)
                                    .orElse(getString(R.string.error_storage_not_writable));

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

    private void onImportCancelled(@NonNull final FinishedMessage<ImportResults> message) {
        closeProgressDialog();

        if (message.isNewEvent()) {
            if (message.result != null) {
                onImportFinished(R.string.progress_end_import_partially_complete, message.result);
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
            ServiceLocator.getInstance().getStyles().updateMenuOrder(getContext());
        }

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
    private String createReport(@NonNull final ImportResults result) {

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
        if (result.styles > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_styles),
                                // deduct built-in styles (remember: MAX_ID is negative)
                                String.valueOf(result.styles + BuiltinStyle.MAX_ID)));
        }
        if (result.preferences > 0) {
            items.add(getString(R.string.lbl_settings));
        }
        if (result.certificates > 0) {
            items.add(getString(R.string.name_colon_value,
                                getString(R.string.lbl_certificates),
                                String.valueOf(result.certificates)));
        }
        final String report = items.stream()
                                   .map(s -> getString(R.string.list_element, s))
                                   .collect(Collectors.joining("\n"));

        int failed = result.failedLinesNr.size();
        if (failed == 0) {
            return report;
        }

        @StringRes
        final int fs;
        if (failed > 10) {
            // keep it sensible, list maximum 10 lines.
            failed = 10;
            fs = R.string.warning_import_failed_for_lines_lots;
        } else {
            fs = R.string.warning_import_failed_for_lines_some;
        }

        final Collection<String> itemList = new ArrayList<>();
        for (int i = 0; i < failed; i++) {
            itemList.add(getString(R.string.a_bracket_b_bracket,
                                   String.valueOf(result.failedLinesNr.get(i)),
                                   result.failedLinesMessage.get(i)));
        }

        return report + "\n" + getString(fs, itemList
                .stream()
                .map(s -> getString(R.string.list_element, s))
                .collect(Collectors.joining("\n")));
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (message.isNewEvent()) {
            if (mProgressDelegate == null) {
                //noinspection ConstantConditions
                mProgressDelegate = new ProgressDelegate(
                        getActivity().findViewById(R.id.progress_frame))
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
}
