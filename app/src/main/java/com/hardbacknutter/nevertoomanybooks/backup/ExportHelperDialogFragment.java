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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogExportOptionsBinding;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.UnexpectedValueException;

public class ExportHelperDialogFragment
        extends OptionsDialogBase<ExportManager> {

    /** Log tag. */
    public static final String TAG = "ExportHelperDialogFrag";

    private ExportHelperViewModel mModel;
    /** View Binding. */
    private DialogExportOptionsBinding mVb;
    /** prevent first-time {@link AdapterView.OnItemSelectedListener#onItemSelected} call. */
    private boolean mArchiveFormatIsSet;

    /**
     * No-arg constructor for OS use.
     */
    public ExportHelperDialogFragment() {
        super(R.layout.dialog_export_options);
    }

    /**
     * Constructor.
     *
     * @param requestKey for use with the FragmentResultListener
     *
     * @return instance
     */
    @NonNull
    public static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                             @NonNull final String requestKey) {
        final DialogFragment frag = new ExportHelperDialogFragment();
        final Bundle args = new Bundle(1);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mModel = new ViewModelProvider(this).get(ExportHelperViewModel.class);
        mModel.init();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mVb = DialogExportOptionsBinding.bind(view);

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
        final ExportManager helper = mModel.getHelper();

        mVb.cbxBooks.setChecked(helper.isOptionSet(Options.BOOKS));
        mVb.cbxBooks.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setOption(Options.BOOKS, isChecked);
            mVb.rbBooksGroup.setEnabled(isChecked);
        });

        final boolean allBooks = !helper.isOptionSet(Options.IS_SYNC);
        mVb.rbBooksAll.setChecked(allBooks);
        mVb.rbBooksSinceLastBackup.setChecked(!allBooks);
        mVb.rbBooksGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // We only have two buttons and one option, so just check the pertinent one.
            helper.setOption(Options.IS_SYNC, checkedId == mVb.rbBooksSinceLastBackup.getId());
        });

        mVb.cbxCovers.setChecked(helper.isOptionSet(Options.COVERS));
        mVb.cbxCovers.setOnCheckedChangeListener(
                (buttonView, isChecked) -> helper.setOption(Options.COVERS, isChecked));

        mVb.cbxPrefsAndStyles.setChecked(helper.isOptionSet(Options.PREFS | Options.STYLES));
        mVb.cbxPrefsAndStyles.setOnCheckedChangeListener((buttonView, isChecked) -> {
            helper.setOption(Options.PREFS, isChecked);
            helper.setOption(Options.STYLES, isChecked);
        });

        // Check options on position.
        final List<String> list = new ArrayList<>();
        list.add(getString(R.string.lbl_archive_type_backup, ArchiveContainer.Zip.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_backup, ArchiveContainer.Tar.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_csv, ArchiveContainer.CsvBooks.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_xml, ArchiveContainer.Xml.getFileExt()));
        list.add(getString(R.string.lbl_archive_type_db, ArchiveContainer.SqLiteDb.getFileExt()));

        // The default selection is index 0, ZIP format.
        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
        mVb.archiveFormatInfoLong.setText("");
        getToolbarConfirmButton().setText(R.string.lbl_backup);


        //noinspection ConstantConditions
        final ArrayAdapter<String> archiveFormatAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, list);
        archiveFormatAdapter.setDropDownViewResource(R.layout.dropdown_menu_popup_item);
        mVb.archiveFormat.setAdapter(archiveFormatAdapter);
        mVb.archiveFormat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(@NonNull final AdapterView<?> parent,
                                       @NonNull final View view,
                                       final int position,
                                       final long id) {
                if (!mArchiveFormatIsSet) {
                    mArchiveFormatIsSet = true;
                    return;
                }

                switch (position) {
                    case 0: {
                        helper.setArchiveContainer(ArchiveContainer.Zip);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
                        mVb.archiveFormatInfoLong.setText("");
                        getToolbarConfirmButton().setText(R.string.lbl_backup);

                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(true);
                        mVb.rbBooksGroup.setEnabled(true);
                        mVb.rbBooksSinceLastBackup.setChecked(true);

                        mVb.cbxCovers.setChecked(true);
                        mVb.cbxCovers.setEnabled(true);

                        mVb.cbxPrefsAndStyles.setChecked(true);
                        mVb.cbxPrefsAndStyles.setEnabled(true);
                        break;
                    }
                    case 1: {
                        helper.setArchiveContainer(ArchiveContainer.Tar);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_backup_info);
                        mVb.archiveFormatInfoLong.setText("");
                        getToolbarConfirmButton().setText(R.string.lbl_backup);

                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(true);
                        mVb.rbBooksGroup.setEnabled(true);
                        mVb.rbBooksSinceLastBackup.setChecked(true);

                        mVb.cbxCovers.setChecked(true);
                        mVb.cbxCovers.setEnabled(true);

                        mVb.cbxPrefsAndStyles.setChecked(true);
                        mVb.cbxPrefsAndStyles.setEnabled(true);
                        break;
                    }
                    case 2: {
                        helper.setArchiveContainer(ArchiveContainer.CsvBooks);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_type_csv_info);
                        mVb.archiveFormatInfoLong.setText("");
                        getToolbarConfirmButton().setText(R.string.lbl_export);

                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(false);
                        mVb.rbBooksGroup.setEnabled(true);
                        mVb.rbBooksSinceLastBackup.setChecked(true);

                        mVb.cbxCovers.setChecked(false);
                        mVb.cbxCovers.setEnabled(false);

                        mVb.cbxPrefsAndStyles.setChecked(false);
                        mVb.cbxPrefsAndStyles.setEnabled(false);
                        break;
                    }
                    case 3: {
                        helper.setArchiveContainer(ArchiveContainer.Xml);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_format_xml_info);
                        mVb.archiveFormatInfoLong.setText(R.string.lbl_archive_is_export_only);
                        getToolbarConfirmButton().setText(R.string.lbl_export);

                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(false);
                        mVb.rbBooksGroup.setEnabled(true);
                        mVb.rbBooksAll.setChecked(true);

                        mVb.cbxCovers.setChecked(false);
                        mVb.cbxCovers.setEnabled(false);

                        mVb.cbxPrefsAndStyles.setChecked(false);
                        mVb.cbxPrefsAndStyles.setEnabled(true);
                        break;
                    }
                    case 4: {
                        helper.setArchiveContainer(ArchiveContainer.SqLiteDb);
                        mVb.archiveFormatInfo.setText(R.string.lbl_archive_format_db_info);
                        mVb.archiveFormatInfoLong.setText(R.string.lbl_archive_is_export_only);
                        getToolbarConfirmButton().setText(R.string.lbl_export);

                        mVb.cbxBooks.setChecked(true);
                        mVb.cbxBooks.setEnabled(false);
                        mVb.rbBooksGroup.setEnabled(false);
                        mVb.rbBooksAll.setChecked(true);

                        mVb.cbxCovers.setChecked(false);
                        mVb.cbxCovers.setEnabled(false);

                        mVb.cbxPrefsAndStyles.setChecked(false);
                        mVb.cbxPrefsAndStyles.setEnabled(false);
                        break;
                    }
                    default:
                        throw new UnexpectedValueException(position);
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
        private ExportManager mHelper;

        /**
         * Pseudo constructor.
         */
        public void init() {
            if (mHelper == null) {
                mHelper = new ExportManager(Options.ENTITIES);
            }
        }

        @NonNull
        ExportManager getHelper() {
            return mHelper;
        }
    }
}
