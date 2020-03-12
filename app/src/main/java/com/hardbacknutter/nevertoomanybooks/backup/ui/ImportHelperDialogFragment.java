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
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveManager;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.backup.archive.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;

public class ImportHelperDialogFragment
        extends OptionsDialogBase<ImportHelper> {

    /** Log tag. */
    public static final String TAG = "ImportHelperDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private ImportHelperViewModel mModel;

    private DialogImportOptionsBinding mVb;

    /**
     * Constructor.
     *
     * @param importHelper import configuration; must have a valid Uri set.
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
        ImportHelper helper = mModel.getHelper();

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

        if (mModel.isArchiveHasValidDates()) {
            final boolean allBooks = !helper.getOption(ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED);
            mVb.rbBooksAll.setChecked(allBooks);
            mVb.infoBtnRbBooksAll.setOnClickListener(v -> infoPopup(mVb.rbBooksAll, v));
            mVb.rbBooksSync.setChecked(!allBooks);
            mVb.infoBtnRbBooksSync.setOnClickListener(v -> infoPopup(mVb.rbBooksSync, v));

            mVb.rbBooksGroup.setOnCheckedChangeListener(
                    // We only have two buttons and one option, so just check the pertinent one.
                    (group, checkedId) -> helper.setOption(ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED,
                                                           checkedId == mVb.rbBooksSync.getId()));
        } else {
            // If the archive does not have a valid creation-date field, then we can't use sync
            mVb.rbBooksGroup.setEnabled(false);
            mVb.rbBooksAll.setChecked(true);
            mVb.rbBooksSync.setChecked(false);
            helper.setOption(ImportHelper.IMPORT_ONLY_NEW_OR_UPDATED, false);
            //noinspection ConstantConditions
            mVb.infoBtnRbBooksSync.setContentDescription(
                    getContext().getString(R.string.warning_import_old_archive));

        }
    }

    public static class ImportHelperViewModel
            extends ViewModel {

        /** import configuration. */
        private ImportHelper mHelper;

        @Nullable
        private ArchiveInfo mInfo;

        private boolean mArchiveHasValidDates;

        public void init(@NonNull final Context context,
                         @NonNull final Bundle args) {
            mHelper = args.getParcelable(BKEY_OPTIONS);
            Objects.requireNonNull(mHelper);
            mInfo = ArchiveManager.getInfo(context, mHelper);
            if (mInfo != null) {
                mArchiveHasValidDates = (mInfo.getCreationDate() != null);
            } else {
                mArchiveHasValidDates = false;
            }
        }

        @NonNull
        ImportHelper getHelper() {
            return mHelper;
        }

        @Nullable
        ArchiveInfo getInfo() {
            return mInfo;
        }

        boolean isArchiveHasValidDates() {
            return mArchiveHasValidDates;
        }
    }
}
