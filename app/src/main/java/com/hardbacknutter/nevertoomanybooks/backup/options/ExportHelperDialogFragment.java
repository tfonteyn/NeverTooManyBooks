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
package com.hardbacknutter.nevertoomanybooks.backup.options;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveType;
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

        mVb.cbxBooks.setChecked(helper.getOption(Options.BOOKS));
        mVb.cbxBooks.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    helper.setOption(Options.BOOKS, isChecked);
                    mVb.rbBooksGroup.setEnabled(isChecked);
                });

        final boolean allBooks = !helper.getOption(ExportHelper.EXPORT_SINCE_LAST_BACKUP);
        mVb.rbBooksAll.setChecked(allBooks);
        mVb.rbBooksSinceLastBackup.setChecked(!allBooks);

        mVb.rbBooksGroup.setOnCheckedChangeListener(
                // We only have two buttons and one option, so just check the pertinent one.
                (group, checkedId) -> helper.setOption(ExportHelper.EXPORT_SINCE_LAST_BACKUP,
                                                       checkedId == mVb.rbBooksSinceLastBackup
                                                               .getId()));

        mVb.cbxCovers.setChecked(helper.getOption(Options.COVERS));
        mVb.cbxCovers.setOnCheckedChangeListener(
                (buttonView, isChecked) -> helper.setOption(Options.COVERS, isChecked));
        mVb.cbxPreferences.setChecked(helper.getOption(
                Options.PREFERENCES | Options.STYLES));
        mVb.cbxPreferences.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    helper.setOption(Options.PREFERENCES, isChecked);
                    helper.setOption(Options.STYLES, isChecked);
                });

        final List<SpinnerEntry> list = new ArrayList<>();
        list.add(new SpinnerEntry(0, getString(R.string.lbl_backup_archive)));
//        list.add(new SpinnerEntry(1, getString(R.string.lbl_import_export_books_as_csv)));
        list.add(new SpinnerEntry(2, getString(R.string.lbl_xml_export)));

        //noinspection ConstantConditions
        ArrayAdapter archiveFormatAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, list);
        archiveFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mVb.archiveFormat.setAdapter(archiveFormatAdapter);
        mVb.archiveFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(@NonNull final AdapterView<?> parent,
                                       @NonNull final View view,
                                       final int position,
                                       final long id) {

                @Nullable
                SpinnerEntry item = (SpinnerEntry) parent.getItemAtPosition(position);
                if (item != null) {
                    if (item.getId() == 0) {
                        helper.setArchiveType(ArchiveType.Tar);
                        mVb.archiveFormatInfo.setVisibility(View.INVISIBLE);
                        mVb.archiveFormatInfo.setText("");
                        mVb.cbxCovers.setChecked(true);
                        mVb.cbxCovers.setEnabled(true);
                        mVb.cbxPreferences.setChecked(true);
                        mVb.cbxPreferences.setEnabled(true);

//                    } else if (item.getId() == 1) {
//                        helper.setArchiveType(ArchiveType.BooksCsv);
//                        mVb.archiveFormatInfo.setVisibility(View.VISIBLE);
//                        mVb.archiveFormatInfo.setText(R.string.info_import_export_option_csv);
//                        mVb.cbxCovers.setChecked(false);
//                        mVb.cbxCovers.setEnabled(false);
//                        mVb.cbxPreferences.setChecked(false);
//                        mVb.cbxPreferences.setEnabled(false);

                    } else if (item.getId() == 2) {
                        helper.setArchiveType(ArchiveType.Xml);
                        mVb.archiveFormatInfo.setVisibility(View.VISIBLE);
                        mVb.archiveFormatInfo.setText(R.string.info_import_export_option_xml);
                        mVb.cbxCovers.setChecked(false);
                        mVb.cbxCovers.setEnabled(false);
                        mVb.cbxPreferences.setChecked(true);
                        mVb.cbxPreferences.setEnabled(true);
                    }
                }
            }

            @Override
            public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                // Do Nothing
            }
        });
    }

    public static class ExportHelperViewModel
            extends ViewModel {

        /** export configuration. */
        private final ExportHelper mHelper = new ExportHelper(ExportHelper.ALL);

        @NonNull
        ExportHelper getHelper() {
            return mHelper;
        }
    }

    /**
     * A generic Spinner entry with an id + title string.
     */
    public static class SpinnerEntry {

        @NonNull
        private final String mTitle;

        @SuppressWarnings("FieldNotUsedInToString")
        private final long mId;

        public SpinnerEntry(final long id,
                            @NonNull final String title) {
            mId = id;
            mTitle = title;
        }

        public long getId() {
            return mId;
        }

        @NonNull
        public String getTitle() {
            return mTitle;
        }

        /**
         * NOT debug, used by the Spinner view.
         *
         * @return entry title
         */
        @Override
        @NonNull
        public String toString() {
            return mTitle;
        }
    }
}
