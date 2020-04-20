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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogExportOptionsBinding;

public class ExportHelperDialogFragment
        extends OptionsDialogBase<ExportManager> {

    /** Log tag. */
    public static final String TAG = "ExportHelperDialogFragment";

    private ExportHelperViewModel mModel;
    /** View Binding. */
    private DialogExportOptionsBinding mVb;

    /**
     * Constructor.
     *
     * @return instance
     */
    @NonNull
    public static DialogFragment newInstance() {
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

        setupOptions();

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .setTitle(R.string.lbl_export_options)
                .setNegativeButton(android.R.string.cancel, (d, w) -> onCancelled())
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        onOptionsSet(mModel.getHelper()))
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        fixDialogWidth();
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setupOptions() {
        ExportManager helper = mModel.getHelper();

        mVb.cbxBooks.setChecked((helper.getOptions() & Options.BOOKS) != 0);
        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setOption(Options.BOOKS, isChecked);
            mVb.rbBooksGroup.setEnabled(isChecked);
        });

        final boolean allBooks =
                (helper.getOptions() & ExportManager.EXPORT_SINCE_LAST_BACKUP) == 0;
        mVb.rbBooksAll.setChecked(allBooks);
        mVb.rbBooksSinceLastBackup.setChecked(!allBooks);

        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // We only have two buttons and one option, so just check the pertinent one.
            helper.setOption(ExportManager.EXPORT_SINCE_LAST_BACKUP,
                             checkedId == mVb.rbBooksSinceLastBackup.getId());
        });

        mVb.cbxCovers.setChecked((helper.getOptions() & Options.COVERS) != 0);
        mVb.cbxCovers.setOnCheckedChangeListener(
                (buttonView, isChecked) -> helper.setOption(Options.COVERS, isChecked));

        mVb.cbxPrefsAndStyles.setChecked(
                (helper.getOptions() & (Options.PREFS | Options.STYLES)) != 0);
        mVb.cbxPrefsAndStyles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setOption(Options.PREFS, isChecked);
            helper.setOption(Options.STYLES, isChecked);
        });

        // Check options on position.
        final List<String> list = new ArrayList<>();
        list.add(getString(R.string.lbl_archive_type_backup, ArchiveContainer.Tar.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_backup, ArchiveContainer.Zip.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_csv, ArchiveContainer.CsvBooks.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_xml, ArchiveContainer.Xml.getFileExt()));

        //noinspection ConstantConditions
        ArrayAdapter archiveFormatAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, list);
        archiveFormatAdapter.setDropDownViewResource(R.layout.dropdown_menu_popup_item);
        mVb.archiveFormat.setAdapter(archiveFormatAdapter);
        mVb.archiveFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(@NonNull final AdapterView<?> parent,
                                       @NonNull final View view,
                                       final int position,
                                       final long id) {

                //noinspection SwitchStatementWithoutDefaultBranch
                switch (position) {
                    case 0:
                        helper.setArchiveContainer(ArchiveContainer.Tar);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(true);
                        mVb.cbxPrefsAndStyles.setChecked(true);
                        mVb.cbxPrefsAndStyles.setEnabled(true);
                        mVb.cbxCovers.setChecked(true);
                        mVb.cbxCovers.setEnabled(true);
                        break;

                    case 1:
                        helper.setArchiveContainer(ArchiveContainer.Zip);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(true);
                        mVb.cbxPrefsAndStyles.setChecked(true);
                        mVb.cbxPrefsAndStyles.setEnabled(true);
                        mVb.cbxCovers.setChecked(true);
                        mVb.cbxCovers.setEnabled(true);
                        break;

                    case 2:
                        helper.setArchiveContainer(ArchiveContainer.CsvBooks);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_csv_info);
                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(false);
                        mVb.cbxPrefsAndStyles.setChecked(false);
                        mVb.cbxPrefsAndStyles.setEnabled(false);
                        mVb.cbxCovers.setChecked(false);
                        mVb.cbxCovers.setEnabled(false);
                        break;

                    case 3:
                        helper.setArchiveContainer(ArchiveContainer.Xml);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_xml_info);
                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(true);
                        mVb.cbxPrefsAndStyles.setChecked(true);
                        mVb.cbxPrefsAndStyles.setEnabled(true);
                        mVb.cbxCovers.setChecked(false);
                        mVb.cbxCovers.setEnabled(false);
                        break;
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
        private final ExportManager mHelper = new ExportManager(Options.ALL);

        @NonNull
        ExportManager getHelper() {
            return mHelper;
        }
    }
}
