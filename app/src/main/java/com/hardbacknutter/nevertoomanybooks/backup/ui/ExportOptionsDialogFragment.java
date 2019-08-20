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
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportOptions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

public class ExportOptionsDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "ExportOptionsDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private ExportOptions mOptions;

    private WeakReference<OptionsListener> mListener;

    private Checkable cbxBooks;
    private Checkable cbxCovers;
    private Checkable cbxPrefs;
    private Checkable cbxXml;
    private Checkable mRadioSinceLastBackup;
    private Checkable mRadioSinceDate;
    private EditText mDateSinceView;

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
        args.putParcelable(BKEY_OPTIONS, options);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : requireArguments();
        mOptions = currentArgs.getParcelable(BKEY_OPTIONS);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_export_options, null);

        cbxBooks = root.findViewById(R.id.cbx_books_csv);
        cbxCovers = root.findViewById(R.id.cbx_covers);
        cbxPrefs = root.findViewById(R.id.cbx_preferences);
        cbxXml = root.findViewById(R.id.cbx_xml_tables);
        mRadioSinceLastBackup = root.findViewById(R.id.radioSinceLastBackup);
        mRadioSinceDate = root.findViewById(R.id.radioSinceDate);
        mDateSinceView = root.findViewById(R.id.txtDate);

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
        outState.putParcelable(BKEY_OPTIONS, mOptions);
    }

    public void setListener(@NonNull final OptionsListener listener) {
        mListener = new WeakReference<>(listener);
    }

    private void updateOptions() {
        // what to export.
        mOptions.what = ExportOptions.NOTHING;

        if (cbxBooks.isChecked()) {
            mOptions.what |= ExportOptions.BOOK_CSV;
        }
        if (cbxCovers.isChecked()) {
            mOptions.what |= ExportOptions.COVERS;
        }
        if (cbxPrefs.isChecked()) {
            mOptions.what |= ExportOptions.PREFERENCES | ExportOptions.BOOK_LIST_STYLES;
        }
        if (cbxXml.isChecked()) {
            mOptions.what |= ExportOptions.XML_TABLES;
        }

        if (mRadioSinceLastBackup.isChecked()) {
            mOptions.what |= ExportOptions.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            mOptions.dateFrom = null;

        } else if (mRadioSinceDate.isChecked()) {
            try {
                mOptions.what |= ExportOptions.EXPORT_SINCE;
                String date = mDateSinceView.getText().toString().trim();
                mOptions.dateFrom = DateUtils.parseDate(date);
            } catch (@NonNull final RuntimeException e) {
                UserMessage.show(mDateSinceView, R.string.hint_date_not_set_with_brackets);
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
