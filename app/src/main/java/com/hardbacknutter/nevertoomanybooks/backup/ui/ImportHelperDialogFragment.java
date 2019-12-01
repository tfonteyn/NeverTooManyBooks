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
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Options;

public class ImportHelperDialogFragment
        extends OptionsDialogBase<ImportHelper> {

    public static final String TAG = "ImportHelperDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private static final String BKEY_ARCHIVE_HAS_VALID_DATES = TAG + ":validDates";

    private ImportHelper mImportHelper;

    private boolean mArchiveHasValidDates;

    private Checkable cbxUpdatedBooks;

    /**
     * Constructor.
     *
     * @param importHelper         import configuration
     * @param archiveHasValidDates flag
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportHelperDialogFragment newInstance(@NonNull final Options importHelper,
                                                         final boolean archiveHasValidDates) {
        ImportHelperDialogFragment frag = new ImportHelperDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(BKEY_OPTIONS, importHelper);
        args.putBoolean(BKEY_ARCHIVE_HAS_VALID_DATES, archiveHasValidDates);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = requireArguments();
        mArchiveHasValidDates = args.getBoolean(BKEY_ARCHIVE_HAS_VALID_DATES, false);

        args = savedInstanceState != null ? savedInstanceState : args;
        mImportHelper = args.getParcelable(BKEY_OPTIONS);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_import_options, null);

        initCommonCbx(mImportHelper, root);

        boolean allBooks = (mImportHelper.options & ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED) == 0;

        Checkable cbxAllBooks = root.findViewById(R.id.radioAllBooks);
        cbxAllBooks.setChecked(allBooks);
        cbxUpdatedBooks = root.findViewById(R.id.radioNewAndUpdatedBooks);
        cbxUpdatedBooks.setChecked(!allBooks);

        if (!mArchiveHasValidDates) {
            cbxAllBooks.setChecked(true);
            cbxUpdatedBooks.setChecked(false);
            ((View) cbxUpdatedBooks).setEnabled(false);
            TextView blurb = root.findViewById(R.id.radioNewAndUpdatedBooksInfo);
            blurb.setText(R.string.import_warning_old_archive);
        }

        //noinspection ConstantConditions
        return new AlertDialog.Builder(getContext())
                .setView(root)
                .setTitle(R.string.import_options_dialog_title)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        updateAndSend(mImportHelper))
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BKEY_OPTIONS, mImportHelper);
    }

    /**
     * Read the checkboxes, and set the options accordingly.
     */
    protected void updateOptions() {
        updateOptions(mImportHelper);

        if (cbxUpdatedBooks.isChecked()) {
            mImportHelper.options |= ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED;
        } else {
            mImportHelper.options &= ~ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED;
        }
    }
}
