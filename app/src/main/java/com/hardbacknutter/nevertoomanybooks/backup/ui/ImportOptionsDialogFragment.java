/*
 * @Copyright 2019 HardBackNutter
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
import android.widget.Checkable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportOptions;

public class ImportOptionsDialogFragment
        extends OptionsDialogBase {

    private static final String BKEY_ARCHIVE_HAS_VALID_DATES = TAG + ":validDates";

    private ImportOptions mOptions;
    private boolean mArchiveHasValidDates;

    private Checkable cbxUpdatedBooks;

    /**
     * Constructor.
     *
     * @param options import configuration
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportOptionsDialogFragment newInstance(@NonNull final ImportOptions options,
                                                          final boolean archiveHasValidDates) {
        ImportOptionsDialogFragment frag = new ImportOptionsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(BKEY_OPTIONS, options);
        args.putBoolean(BKEY_ARCHIVE_HAS_VALID_DATES, archiveHasValidDates);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : requireArguments();
        mOptions = currentArgs.getParcelable(BKEY_OPTIONS);
        mArchiveHasValidDates = currentArgs.getBoolean(BKEY_ARCHIVE_HAS_VALID_DATES);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_import_options, null);

        initCommonCbx(mOptions, root);

        Checkable cbxAllBooks = root.findViewById(R.id.radioAllBooks);
        cbxAllBooks.setChecked((mOptions.what & ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED) == 0);
        cbxUpdatedBooks = root.findViewById(R.id.radioNewAndUpdatedBooks);
        cbxUpdatedBooks.setChecked((mOptions.what & ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED) != 0);

        if (!mArchiveHasValidDates) {
            cbxAllBooks.setChecked(true);
            cbxUpdatedBooks.setChecked(false);
            ((View) cbxUpdatedBooks).setEnabled(false);
            TextView blurb = root.findViewById(R.id.radioNewAndUpdatedBooksInfo);
            blurb.setText(R.string.import_warning_old_archive);
        }

        //noinspection ConstantConditions
        AlertDialog dialog =
                new AlertDialog.Builder(getContext())
                        .setView(root)
                        .setTitle(R.string.import_options_dialog_title)
                        .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                        .setPositiveButton(android.R.string.ok,
                                           (d, which) -> updateAndSend(mOptions))
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BKEY_OPTIONS, mOptions);
    }

    /**
     * Read the checkboxes, and set the options accordingly.
     */
    protected void updateOptions() {
        updateOptions(mOptions);

        if (cbxUpdatedBooks.isChecked()) {
            mOptions.what |= ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED;
        } else {
            mOptions.what &= ~ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED;
        }
    }
}
