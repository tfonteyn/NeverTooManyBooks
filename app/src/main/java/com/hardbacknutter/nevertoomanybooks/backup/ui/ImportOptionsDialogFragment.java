/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
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
import androidx.fragment.app.DialogFragment;

import java.io.IOException;
import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.backup.BackupManager;
import com.hardbacknutter.nevertoomanybooks.backup.ImportOptions;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupInfo;
import com.hardbacknutter.nevertoomanybooks.backup.archivebase.BackupReader;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class ImportOptionsDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "ImportOptionsDialogFragment";

    private ImportOptions mOptions;

    private WeakReference<OptionsListener> mListener;

    /**
     * Constructor.
     *
     * @param options import configuration
     *
     * @return Created fragment
     */
    @NonNull
    public static ImportOptionsDialogFragment newInstance(@NonNull final ImportOptions options) {
        ImportOptionsDialogFragment frag = new ImportOptionsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_OPTIONS, options);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mOptions = args.getParcelable(UniqueId.BKEY_IMPORT_EXPORT_OPTIONS);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_import_options, null);

        if (!archiveHasValidDates()) {
            View radioNewAndUpdatedBooks = root.findViewById(R.id.radioNewAndUpdatedBooks);
            radioNewAndUpdatedBooks.setEnabled(false);
            TextView blurb = root.findViewById(R.id.radioNewAndUpdatedBooksInfo);
            blurb.setText(R.string.import_warning_old_archive);
        }

        //noinspection ConstantConditions
        AlertDialog dialog =
                new AlertDialog.Builder(getContext())
                        .setView(root)
                        .setTitle(R.string.import_options_dialog_title)
                        .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                        .setPositiveButton(android.R.string.ok, (d, which) -> {
                            updateOptions();
                            if (mListener.get() != null) {
                                mListener.get().onOptionsSet(mOptions);
                            } else {
                                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                                    Logger.debug(this, "onOptionsSet",
                                                 Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
                                }
                            }
                        })
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_OPTIONS, mOptions);
    }

    public void setListener(@NonNull final OptionsListener listener) {
        mListener = new WeakReference<>(listener);
    }

    private void updateOptions() {
        Dialog dialog = getDialog();
        // what to import. All three checked == ImportOptions.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.cbx_books_csv)).isChecked()) {
            mOptions.what |= ImportOptions.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_covers)).isChecked()) {
            mOptions.what |= ImportOptions.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_preferences)).isChecked()) {
            mOptions.what |= ImportOptions.PREFERENCES | ImportOptions.BOOK_LIST_STYLES;
        }

        Checkable radioNewAndUpdatedBooks = dialog.findViewById(R.id.radioNewAndUpdatedBooks);
        if (radioNewAndUpdatedBooks.isChecked()) {
            mOptions.what |= ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED;
        }
    }

    /**
     * read the info block and check if we have valid dates.
     */
    private boolean archiveHasValidDates() {
        boolean hasValidDates;
        //noinspection ConstantConditions
        try (BackupReader reader = BackupManager.getReader(getContext(), mOptions.file)) {
            BackupInfo info = reader.getInfo();
            reader.close();
            hasValidDates = info.getAppVersionCode() >= 152;
        } catch (@NonNull final IOException e) {
            Logger.error(this, e);
            hasValidDates = false;
        }
        return hasValidDates;
    }

    @Override
    public void onPause() {
        updateOptions();
        super.onPause();
    }

    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     */
    public interface OptionsListener {

        void onOptionsSet(@NonNull ImportOptions options);
    }
}
