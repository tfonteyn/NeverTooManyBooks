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
package com.hardbacknutter.nevertoomanybooks.backup.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;

public class ImportHelperDialogFragment
        extends OptionsDialogBase<ImportHelper> {

    public static final String TAG = "ImportHelperDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private static final String BKEY_ARCHIVE_HAS_VALID_DATES = TAG + ":validDates";

    private ImportHelper mImportHelper;

    private BackupInfo mInfo;

    private boolean mArchiveHasValidDates;
    private DialogImportOptionsBinding mVb;

    /**
     * Constructor.
     *
     * @param importHelper import configuration
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportHelperDialogFragment newInstance(@NonNull final Options importHelper) {
        ImportHelperDialogFragment frag = new ImportHelperDialogFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(BKEY_OPTIONS, importHelper);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            Bundle args = requireArguments();
            mImportHelper = args.getParcelable(BKEY_OPTIONS);
            Objects.requireNonNull(mImportHelper);
            Objects.requireNonNull(mImportHelper.uri, ErrorMsg.NULL_URI);
            //noinspection ConstantConditions
            mInfo = BackupManager.getInfo(getContext(), mImportHelper.uri);
            if (mInfo != null) {
                mArchiveHasValidDates = (mInfo.getCreationDate() != null);
            } else {
                mArchiveHasValidDates = false;
            }
        } else {
            mImportHelper = savedInstanceState.getParcelable(BKEY_OPTIONS);
            mArchiveHasValidDates = savedInstanceState.getBoolean(BKEY_ARCHIVE_HAS_VALID_DATES,
                                                                  false);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        mVb = DialogImportOptionsBinding.inflate(inflater);

        //URGENT: create a viewmodel, store info block, and ImportHelper etc etc
        setOptions();

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .setTitle(R.string.title_import_options)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        onConfirmOptions(mImportHelper))
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BKEY_OPTIONS, mImportHelper);
        outState.putBoolean(BKEY_ARCHIVE_HAS_VALID_DATES, mArchiveHasValidDates);
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setOptions() {
        mVb.cbxBooksCsv.setChecked((mImportHelper.options & Options.BOOK_CSV) != 0);
        mVb.cbxCovers.setChecked((mImportHelper.options & Options.COVERS) != 0);
        mVb.cbxPreferences.setChecked(
                (mImportHelper.options & (Options.PREFERENCES | Options.BOOK_LIST_STYLES)) != 0);

        // Radio group
        final boolean allBooks =
                (mImportHelper.options & ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED) == 0;
        mVb.rbBooksAll.setChecked(allBooks);
        mVb.infoBtnRbBooksAll.setOnClickListener(v -> infoPopup(mVb.rbBooksAll, v));
        mVb.rbBooksSync.setChecked(!allBooks);
        mVb.infoBtnRbBooksSync.setOnClickListener(v -> infoPopup(mVb.rbBooksSync, v));

        // If the archive does not have a valid creation-date field, then we can't use sync
        if (!mArchiveHasValidDates) {
            mVb.rbBooksAll.setChecked(true);
            mVb.rbBooksSync.setChecked(false);
            ((View) mVb.rbBooksSync).setEnabled(false);
            //noinspection ConstantConditions
            mVb.infoBtnRbBooksSync.setContentDescription(
                    getContext().getString(R.string.warning_import_old_archive));
        }
    }

    @Override
    public void onPause() {
        getOptions(mImportHelper);
        super.onPause();
    }

    /**
     * Read the checkboxes, and set the options accordingly.
     *
     * @param options to populate
     */
    protected void getOptions(@NonNull final ImportHelper options) {
        options.setOption(Options.BOOK_CSV, mVb.cbxBooksCsv.isChecked());
        options.setOption(Options.COVERS, mVb.cbxCovers.isChecked());
        options.setOption(Options.PREFERENCES | Options.BOOK_LIST_STYLES,
                          mVb.cbxPreferences.isChecked());

        // radio group
        options.setOption(ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED, mVb.rbBooksSync.isChecked());
    }
}
