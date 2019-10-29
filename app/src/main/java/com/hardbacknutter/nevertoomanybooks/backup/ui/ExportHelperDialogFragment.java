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

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.Options;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.UserMessage;

public class ExportHelperDialogFragment
        extends OptionsDialogBase<ExportHelper> {

    public static final String TAG = "ExportHelperDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private ExportHelper mExportHelper;

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
    public static ExportHelperDialogFragment newInstance(@NonNull final ExportHelper options) {
        ExportHelperDialogFragment frag = new ExportHelperDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(BKEY_OPTIONS, options);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : requireArguments();
        mExportHelper = currentArgs.getParcelable(BKEY_OPTIONS);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        View root = layoutInflater.inflate(R.layout.dialog_export_options, null);

        initCommonCbx(mExportHelper, root);

        mRadioSinceLastBackup = root.findViewById(R.id.radioSinceLastBackup);
        mRadioSinceDate = root.findViewById(R.id.radioSinceDate);
        mDateSinceView = root.findViewById(R.id.date_since);

        //noinspection ConstantConditions

        return new AlertDialog.Builder(getContext())
                .setView(root)
                .setTitle(R.string.export_options_dialog_title)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dismiss())
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        updateAndSend(mExportHelper))
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BKEY_OPTIONS, mExportHelper);
    }

    /**
     * Read the checkboxes, and set the options accordingly.
     */
    protected void updateOptions() {
        updateOptions(mExportHelper);

        if (mRadioSinceLastBackup.isChecked()) {
            mExportHelper.options |= ExportHelper.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            mExportHelper.setDateFrom(null);
        } else {
            mExportHelper.options &= ~ExportHelper.EXPORT_SINCE;
        }

        if (mRadioSinceDate.isChecked()) {
            try {
                mExportHelper.options |= ExportHelper.EXPORT_SINCE;
                String date = mDateSinceView.getText().toString().trim();
                mExportHelper.setDateFrom(DateUtils.parseDate(date));
            } catch (@NonNull final RuntimeException e) {
                UserMessage.show(mDateSinceView, R.string.warning_requires_date);
                mExportHelper.options = Options.NOTHING;
            }
        } else {
            mExportHelper.options &= ~ExportHelper.EXPORT_SINCE;
        }
    }
}
