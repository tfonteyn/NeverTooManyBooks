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

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.DialogFragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;

public class ImportOptionsDialogFragment
        extends BaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "ImportOptionsDialogFragment";
    protected static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

    private ImportViewModel mImportViewModel;
    /** View Binding. */
    private DialogImportOptionsBinding mVb;
    private boolean mAllowSetEnableOnBooksGroup;

    /**
     * No-arg constructor for OS use.
     */
    public ImportOptionsDialogFragment() {
        super(R.layout.dialog_import_options);
        setFloatingDialogWidth(R.dimen.floating_dialogs_import_options_width);
    }


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRequestKey = Objects.requireNonNull(requireArguments().getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogImportOptionsBinding.bind(view);

        //noinspection ConstantConditions
        mImportViewModel = new ViewModelProvider(getActivity()).get(ImportViewModel.class);
        try {
            setupOptions();

        } catch (@NonNull final InvalidArchiveException e) {
            finishActivityWithErrorMessage(mVb.bodyFrame, R.string.error_import_file_not_supported);

        } catch (@NonNull final IOException e) {
            finishActivityWithErrorMessage(mVb.bodyFrame, R.string.error_import_failed);
        }
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        sendResult(false);
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            sendResult(mImportViewModel.getImportHelper().hasEntityOption());
            return true;
        }
        return false;
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     *
     * @throws InvalidArchiveException on failure to recognise a supported archive
     * @throws IOException             on other failures
     */
    private void setupOptions()
            throws InvalidArchiveException, IOException {
        final ImportHelper helper = mImportViewModel.getImportHelper();

        // Indicates if we're importing from a file only containing books.
        //noinspection ConstantConditions
        final boolean isBooksOnly = helper.isBooksOnlyContainer(getContext());

        if (isBooksOnly) {
            // CSV files don't have options other than the books.
            mVb.cbxGroup.setVisibility(View.GONE);

        } else {
            // Populate the options.
            mVb.cbxBooks.setChecked(helper.isOptionSet(ImportHelper.Options.BOOKS));
            mVb.cbxBooks.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(ImportHelper.Options.BOOKS, isChecked);
                        if (mAllowSetEnableOnBooksGroup) {
                            mVb.rbBooksGroup.setEnabled(isChecked);
                        }
                    });

            mVb.cbxCovers.setChecked(helper.isOptionSet(ImportHelper.Options.COVERS));
            mVb.cbxCovers.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> helper
                            .setOption(ImportHelper.Options.COVERS, isChecked));

            mVb.cbxPrefsAndStyles.setChecked(helper.isOptionSet(
                    ImportHelper.Options.PREFS | ImportHelper.Options.STYLES));
            mVb.cbxPrefsAndStyles.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(ImportHelper.Options.PREFS, isChecked);
                        helper.setOption(ImportHelper.Options.STYLES, isChecked);
                    });
        }

        final LocalDateTime archiveCreationDate = helper.getArchiveCreationDate(getContext());
        // enable or disable the sync option
        if (isBooksOnly || archiveCreationDate != null) {
            mAllowSetEnableOnBooksGroup = true;
            final boolean allBooks = !helper.isOptionSet(ImportHelper.Options.IS_SYNC);
            mVb.rbBooksAll.setChecked(allBooks);
            mVb.infoBtnRbBooksAll.setOnClickListener(StandardDialogs::infoPopup);
            mVb.rbBooksSync.setChecked(!allBooks);
            mVb.infoBtnRbBooksSync.setOnClickListener(StandardDialogs::infoPopup);

            mVb.rbBooksGroup.setOnCheckedChangeListener(
                    // We only have two buttons and one option, so just check the pertinent one.
                    (group, checkedId) -> helper.setOption(ImportHelper.Options.IS_SYNC,
                                                           checkedId == mVb.rbBooksSync.getId()));
        } else {
            // If the archive does not have a valid creation-date field, then we can't use sync
            // TODO Maybe change string to "... archive is missing a creation date field.
            mVb.rbBooksGroup.setEnabled(false);
            mAllowSetEnableOnBooksGroup = false;

            mVb.rbBooksAll.setChecked(true);
            mVb.rbBooksSync.setChecked(false);
            helper.setOption(ImportHelper.Options.IS_SYNC, false);
            mVb.infoBtnRbBooksSync.setContentDescription(
                    getString(R.string.warning_import_old_archive));
        }
    }

    protected void sendResult(final boolean startTask) {
        Launcher.sendResult(this, mRequestKey, startTask);
        dismiss();
    }

    public abstract static class Launcher
            extends DialogFragmentLauncherBase {

        private static final String START_TASK = "startTask";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               final boolean startTask) {
            final Bundle result = new Bundle(1);
            result.putBoolean(START_TASK, startTask);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         */
        public void launch() {
            final Bundle args = new Bundle(1);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);

            final DialogFragment frag = new ImportOptionsDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, ImportOptionsDialogFragment.TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(result.getBoolean(START_TASK));
        }

        /**
         * Callback handler.
         *
         * @param startTask {@code true} if the user confirmed the dialog.
         *                  i.e. if the import task should be started
         */
        public abstract void onResult(boolean startTask);
    }
}
