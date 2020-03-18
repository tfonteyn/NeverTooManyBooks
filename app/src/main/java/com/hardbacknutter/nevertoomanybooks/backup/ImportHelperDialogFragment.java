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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;

public class ImportHelperDialogFragment
        extends OptionsDialogBase<ImportManager> {

    /** Log tag. */
    public static final String TAG = "ImportHelperDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private ImportHelperViewModel mModel;

    private DialogImportOptionsBinding mVb;

    /**
     * Constructor.
     *
     * @param manager import configuration; must have a valid Uri set.
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportHelperDialogFragment newInstance(@NonNull final ImportManager manager) {
        ImportHelperDialogFragment frag = new ImportHelperDialogFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(BKEY_OPTIONS, manager);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        mModel = new ViewModelProvider(this).get(ImportHelperViewModel.class);
        //noinspection ConstantConditions
        mModel.init(getContext(), requireArguments());

        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        mVb = DialogImportOptionsBinding.inflate(inflater);

        setupOptions();

        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .setTitle(R.string.title_import_options)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        onOptionsSet(mModel.getHelper()))
                .create();
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setupOptions() {
        ImportManager helper = mModel.getHelper();

        //noinspection ConstantConditions
        boolean isCsvBooks = ArchiveContainer.CsvBooks.equals(helper.getContainer(getContext()));
        if (isCsvBooks) {
            // CSV files don't have options other then the books.
            mVb.cbxGroup.setVisibility(View.GONE);
        } else {
            // Populate the options.
            mVb.cbxBooks.setChecked((helper.getOptions() & Options.BOOKS) != 0);
            mVb.cbxBooks.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(Options.BOOKS, isChecked);
                        mVb.rbBooksGroup.setEnabled(isChecked);
                    });

            mVb.cbxCovers.setChecked((helper.getOptions() & Options.COVERS) != 0);
            mVb.cbxCovers.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> helper.setOption(Options.COVERS, isChecked));

            mVb.cbxPreferences.setChecked(
                    (helper.getOptions() & (Options.PREFERENCES | Options.STYLES)) != 0);
            mVb.cbxPreferences.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(Options.PREFERENCES, isChecked);
                        helper.setOption(Options.STYLES, isChecked);
                    });
        }

        // enable or disable the sync option
        if (isCsvBooks || mModel.getArchiveCreationDate() != null) {
            final boolean allBooks = (helper.getOptions()
                                      & ImportManager.IMPORT_ONLY_NEW_OR_UPDATED) == 0;
            mVb.rbBooksAll.setChecked(allBooks);
            mVb.infoBtnRbBooksAll.setOnClickListener(v -> infoPopup(mVb.rbBooksAll, v));
            mVb.rbBooksSync.setChecked(!allBooks);
            mVb.infoBtnRbBooksSync.setOnClickListener(v -> infoPopup(mVb.rbBooksSync, v));

            mVb.rbBooksGroup.setOnCheckedChangeListener(
                    // We only have two buttons and one option, so just check the pertinent one.
                    (group, checkedId) -> helper.setOption(ImportManager.IMPORT_ONLY_NEW_OR_UPDATED,
                                                           checkedId == mVb.rbBooksSync.getId()));
        } else {
            // If the archive does not have a valid creation-date field, then we can't use sync
            mVb.rbBooksGroup.setEnabled(false);
            mVb.rbBooksAll.setChecked(true);
            mVb.rbBooksSync.setChecked(false);
            helper.setOption(ImportManager.IMPORT_ONLY_NEW_OR_UPDATED, false);
            mVb.infoBtnRbBooksSync.setContentDescription(
                    getContext().getString(R.string.warning_import_old_archive));

        }
    }

    public static class ImportHelperViewModel
            extends ViewModel {

        /** import configuration. */
        private ImportManager mHelper;

        @Nullable
        private ArchiveInfo mInfo;

        public void init(@NonNull final Context context,
                         @NonNull final Bundle args) {
            mHelper = args.getParcelable(BKEY_OPTIONS);
            Objects.requireNonNull(mHelper);
            try {
                mInfo = mHelper.getInfo(context);
            } catch (@NonNull final IOException | InvalidArchiveException e) {
                // We should never get here, as the archive being valid should have been
                // checked before creating the dialog.
                throw new IllegalStateException(e);
            }
        }

        @NonNull
        ImportManager getHelper() {
            return mHelper;
        }

        @Nullable
        ArchiveInfo getInfo() {
            return mInfo;
        }

        @Nullable
        Date getArchiveCreationDate() {
            if (mInfo == null) {
                return null;
            } else {
                return mInfo.getCreationDate();
            }
        }
    }
}
