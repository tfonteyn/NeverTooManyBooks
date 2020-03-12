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
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;

public class ImportCsvHelperDialogFragment
        extends OptionsDialogBase<ImportHelper> {

    /** Log tag. */
    public static final String TAG = "ImportCsvHelperDialog";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private ImportCsvHelperViewModel mModel;

    private DialogImportOptionsBinding mVb;

    /**
     * Constructor.
     *
     * @param importHelper import configuration; must have a valid Uri set.
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportCsvHelperDialogFragment newInstance(@NonNull final Options importHelper) {
        ImportCsvHelperDialogFragment frag = new ImportCsvHelperDialogFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(BKEY_OPTIONS, importHelper);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        mModel = new ViewModelProvider(this).get(ImportCsvHelperViewModel.class);
        mModel.init(requireArguments());

        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        mVb = DialogImportOptionsBinding.inflate(inflater);

        setupOptions();

        //noinspection ConstantConditions
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
        ImportHelper helper = mModel.getHelper();

        // remove all checkboxes, leaving only the radio buttons for importing books.
        mVb.cbxGroup.setVisibility(View.GONE);

        final boolean allBooks = !helper.getOption(ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED);
        mVb.rbBooksAll.setChecked(allBooks);
        mVb.infoBtnRbBooksAll.setOnClickListener(v -> infoPopup(mVb.rbBooksAll, v));
        mVb.rbBooksSync.setChecked(!allBooks);
        mVb.infoBtnRbBooksSync.setOnClickListener(v -> infoPopup(mVb.rbBooksSync, v));

        mVb.rbBooksGroup.setOnCheckedChangeListener(
                // We only have two buttons and one option, so just check the pertinent one.
                (group, checkedId) -> helper.setOption(ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED,
                                                       checkedId == mVb.rbBooksSync.getId()));
    }

    public static class ImportCsvHelperViewModel
            extends ViewModel {

        /** import configuration. */
        private ImportHelper mHelper;

        public void init(@NonNull final Bundle args) {
            mHelper = args.getParcelable(BKEY_OPTIONS);
            Objects.requireNonNull(mHelper);
        }

        @NonNull
        ImportHelper getHelper() {
            return mHelper;
        }
    }
}
