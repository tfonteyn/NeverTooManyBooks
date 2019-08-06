/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.backup.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Checkable;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.UniqueId;
import com.hardbacknutter.nevertomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertomanybooks.debug.Logger;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.UserMessage;

public class ExportOptionsDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "ExportOptionsDialogFragment";

    private ExportOptions mOptions;

    private WeakReference<OptionsListener> mListener;

    /**
     * Constructor.
     *
     * @param options export configuration
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportOptionsDialogFragment newInstance(@NonNull final ExportOptions options) {
        ExportOptionsDialogFragment frag = new ExportOptionsDialogFragment();
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
        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater().inflate(R.layout.dialog_export_options, null);

        //noinspection ConstantConditions
        AlertDialog dialog =
                new AlertDialog.Builder(getContext())
                        .setView(root)
                        .setTitle(R.string.export_options_dialog_title)
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
        // what to export. All checked == ExportOptions.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.cbx_books_csv)).isChecked()) {
            mOptions.what |= ExportOptions.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_covers)).isChecked()) {
            mOptions.what |= ExportOptions.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_preferences)).isChecked()) {
            mOptions.what |= ExportOptions.PREFERENCES | ExportOptions.BOOK_LIST_STYLES;
        }
        // This one is not a part of ExportOptions.ALL, the user must explicitly want it.
        if (((Checkable) dialog.findViewById(R.id.cbx_xml_tables)).isChecked()) {
            mOptions.what |= ExportOptions.XML_TABLES;
        }

        Checkable radioSinceLastBackup = dialog.findViewById(R.id.radioSinceLastBackup);
        Checkable radioSinceDate = dialog.findViewById(R.id.radioSinceDate);

        if (radioSinceLastBackup.isChecked()) {
            mOptions.what |= ExportOptions.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            mOptions.dateFrom = null;

        } else if (radioSinceDate.isChecked()) {
            EditText dateSinceView = dialog.findViewById(R.id.txtDate);
            try {
                mOptions.what |= ExportOptions.EXPORT_SINCE;
                mOptions.dateFrom =
                        DateUtils.parseDate(dateSinceView.getText().toString().trim());
            } catch (@NonNull final RuntimeException e) {
                UserMessage.show(dateSinceView, R.string.hint_date_not_set_with_brackets);
                mOptions.what = ExportOptions.NOTHING;
            }
        }
    }

    @Override
    public void onPause() {
        updateOptions();
        super.onPause();
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed.
     */
    public interface OptionsListener {

        void onOptionsSet(@NonNull ExportOptions options);
    }
}
