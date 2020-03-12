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
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogExportOptionsBinding;

public class ExportHelperDialogFragment
        extends OptionsDialogBase<ExportHelper> {

    /** Log tag. */
    public static final String TAG = "ExportHelperDialogFragment";

    private ExportHelperViewModel mModel;

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

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        mModel = new ViewModelProvider(this).get(ExportHelperViewModel.class);

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
                        onOptionsSet(mModel.getHelper()))
                .create();
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setOptions() {
        ExportHelper helper = mModel.getHelper();

        mVb.cbxBooksCsv.setChecked(helper.getOption(Options.BOOK_CSV));
        mVb.cbxBooksCsv.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    helper.setOption(Options.BOOK_CSV, isChecked);
                    mVb.rbBooksGroup.setEnabled(isChecked);
                });
        mVb.cbxCovers.setChecked(helper.getOption(Options.COVERS));
        mVb.cbxCovers.setOnCheckedChangeListener(
                (buttonView, isChecked) -> helper.setOption(Options.COVERS, isChecked));
        mVb.cbxPreferences.setChecked(helper.getOption(
                Options.PREFERENCES | Options.BOOK_LIST_STYLES));
        mVb.cbxPreferences.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    helper.setOption(Options.PREFERENCES, isChecked);
                    helper.setOption(Options.BOOK_LIST_STYLES, isChecked);
                });
        mVb.cbxXmlTables.setChecked(helper.getOption(Options.XML_TABLES));
        mVb.cbxXmlTables.setOnCheckedChangeListener(
                (buttonView, isChecked) -> helper.setOption(Options.XML_TABLES, isChecked));

        final boolean allBooks = !helper.getOption(ExportHelper.EXPORT_SINCE_LAST_BACKUP);
        mVb.rbBooksAll.setChecked(allBooks);
        mVb.rbBooksSinceLastBackup.setChecked(!allBooks);

        mVb.rbBooksGroup.setOnCheckedChangeListener(
                // We only have two buttons and one option, so just check the pertinent one.
                (group, checkedId) -> helper.setOption(ExportHelper.EXPORT_SINCE_LAST_BACKUP,
                                                       checkedId == mVb.rbBooksSinceLastBackup
                                                               .getId()));
    }

    public static class ExportHelperViewModel
            extends ViewModel {

        /**  export configuration. */
        private final ExportHelper mHelper = new ExportHelper(ExportHelper.ALL);

        @NonNull
        ExportHelper getHelper() {
            return mHelper;
        }
    }
}
