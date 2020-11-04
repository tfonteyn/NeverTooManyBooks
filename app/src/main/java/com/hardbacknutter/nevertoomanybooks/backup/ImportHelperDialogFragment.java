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
import androidx.lifecycle.ViewModelProvider;

import java.io.IOException;
import java.time.LocalDateTime;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;

public class ImportHelperDialogFragment
        extends OptionsDialogBase<ImportManager> {

    /** Log tag. */
    public static final String TAG = "ImportHelperDialogFragment";
    static final String BKEY_IMPORT_MANAGER = TAG + ":helper";

    private ImportHelperViewModel mModel;
    /** View Binding. */
    private DialogImportOptionsBinding mVb;
    /** Indicates if we're importing from a file only containing books. */
    private boolean mIsBooksOnly;
    private boolean mAllowSetEnableOnBooksGroup;

    /**
     * No-arg constructor for OS use.
     */
    public ImportHelperDialogFragment() {
        super(R.layout.dialog_import_options);
        setFloatingDialogWidth(R.dimen.floating_dialogs_import_options_width);
    }

    /**
     * Constructor.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param helper     import configuration; must have a valid Uri set.
     *
     * @return instance
     */
    @NonNull
    public static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                             @NonNull final String requestKey,
                                             @NonNull final ImportManager helper) {
        final DialogFragment frag = new ImportHelperDialogFragment();
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(BKEY_IMPORT_MANAGER, helper);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogImportOptionsBinding.bind(view);

        mModel = new ViewModelProvider(this).get(ImportHelperViewModel.class);
        try {
            //noinspection ConstantConditions
            mModel.init(getContext(), requireArguments());

        } catch (@NonNull final InvalidArchiveException e) {
            finishActivityWithErrorMessage(mVb.bodyFrame, R.string.error_import_file_not_supported);
            return;

        } catch (@NonNull final IOException e) {
            finishActivityWithErrorMessage(mVb.bodyFrame, R.string.error_import_failed);
            return;
        }

        mIsBooksOnly = mModel.isBooksOnlyContainer(getContext());

        setupOptions();
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        onCancelled();
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (mModel.getHelper().hasEntityOption()) {
                onOptionsSet(mModel.getHelper());
            } else {
                onCancelled();
            }
            return true;
        }
        return false;
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setupOptions() {
        final ImportManager helper = mModel.getHelper();

        if (mIsBooksOnly) {
            // CSV files don't have options other than the books.
            mVb.cbxGroup.setVisibility(View.GONE);

        } else {
            // Populate the options.
            mVb.cbxBooks.setChecked(helper.isOptionSet(Options.BOOKS));
            mVb.cbxBooks.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(Options.BOOKS, isChecked);
                        if (mAllowSetEnableOnBooksGroup) {
                            mVb.rbBooksGroup.setEnabled(isChecked);
                        }
                    });

            mVb.cbxCovers.setChecked(helper.isOptionSet(Options.COVERS));
            mVb.cbxCovers.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> helper.setOption(Options.COVERS, isChecked));

            mVb.cbxPrefsAndStyles.setChecked(helper.isOptionSet(Options.PREFS | Options.STYLES));
            mVb.cbxPrefsAndStyles.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(Options.PREFS, isChecked);
                        helper.setOption(Options.STYLES, isChecked);
                    });
        }

        //noinspection ConstantConditions
        final LocalDateTime archiveCreationDate = mModel.getArchiveCreationDate(getContext());
        // enable or disable the sync option
        if (mIsBooksOnly || archiveCreationDate != null) {
            mAllowSetEnableOnBooksGroup = true;
            final boolean allBooks = !helper.isOptionSet(Options.IS_SYNC);
            mVb.rbBooksAll.setChecked(allBooks);
            mVb.infoBtnRbBooksAll.setOnClickListener(StandardDialogs::infoPopup);
            mVb.rbBooksSync.setChecked(!allBooks);
            mVb.infoBtnRbBooksSync.setOnClickListener(StandardDialogs::infoPopup);

            mVb.rbBooksGroup.setOnCheckedChangeListener(
                    // We only have two buttons and one option, so just check the pertinent one.
                    (group, checkedId) -> helper.setOption(Options.IS_SYNC,
                                                           checkedId == mVb.rbBooksSync.getId()));
        } else {
            // If the archive does not have a valid creation-date field, then we can't use sync
            // TODO Maybe change string to "... archive is missing a creation date field.
            mVb.rbBooksGroup.setEnabled(false);
            mAllowSetEnableOnBooksGroup = false;

            mVb.rbBooksAll.setChecked(true);
            mVb.rbBooksSync.setChecked(false);
            helper.setOption(Options.IS_SYNC, false);
            mVb.infoBtnRbBooksSync.setContentDescription(
                    getString(R.string.warning_import_old_archive));
        }
    }

}
