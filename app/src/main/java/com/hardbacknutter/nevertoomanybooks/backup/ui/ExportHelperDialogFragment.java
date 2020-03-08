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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogExportOptionsBinding;

public class ExportHelperDialogFragment
        extends OptionsDialogBase<ExportHelper> {

    public static final String TAG = "ExportHelperDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private ExportHelper mExportHelper;

    private DialogExportOptionsBinding mVb;

    /**
     * Constructor.
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportHelperDialogFragment newInstance() {
        return new ExportHelperDialogFragment();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            mExportHelper = new ExportHelper(ExportHelper.ALL);
        } else {
            mExportHelper = savedInstanceState.getParcelable(BKEY_OPTIONS);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        mVb = DialogExportOptionsBinding.inflate(inflater);

        setOptions();

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .setTitle(R.string.title_export_options)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        onConfirmOptions(mExportHelper))
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BKEY_OPTIONS, mExportHelper);
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setOptions() {
        mVb.cbxBooksCsv.setChecked((mExportHelper.options & Options.BOOK_CSV) != 0);
        mVb.cbxCovers.setChecked((mExportHelper.options & Options.COVERS) != 0);
        mVb.cbxPreferences.setChecked(
                (mExportHelper.options & (Options.PREFERENCES | Options.BOOK_LIST_STYLES)) != 0);
        mVb.cbxXmlTables.setChecked((mExportHelper.options & Options.XML_TABLES) != 0);

        // Radio group
        final boolean allBooks =
                (mExportHelper.options & ExportHelper.EXPORT_SINCE_LAST_BACKUP) == 0;
        mVb.rbBooksAll.setChecked(allBooks);
//        mVb.infoBtnRbBooksAll.setOnClickListener(v -> infoPopup(v, mVb.rbBooksAll));
        mVb.rbBooksSinceLastBackup.setChecked(!allBooks);
//        mVb.infoBtnRbBooksSync.setOnClickListener(v -> infoPopup(v, mVb.rbBooksSinceLastBackup));
    }

    @Override
    public void onPause() {
        getOptions(mExportHelper);
        super.onPause();
    }

    /**
     * Read the checkboxes/radio-buttons, and set the options accordingly.
     *
     * @param options to populate
     */
    protected void getOptions(@NonNull final ExportHelper options) {

        options.setOption(Options.BOOK_CSV, mVb.cbxBooksCsv.isChecked());
        options.setOption(Options.COVERS, mVb.cbxCovers.isChecked());
        options.setOption(Options.PREFERENCES | Options.BOOK_LIST_STYLES,
                          mVb.cbxPreferences.isChecked());
        options.setOption(Options.XML_TABLES, mVb.cbxXmlTables.isChecked());

        // radio group
        options.setOption(ExportHelper.EXPORT_SINCE_LAST_BACKUP,
                          mVb.rbBooksSinceLastBackup.isChecked());
    }
}
