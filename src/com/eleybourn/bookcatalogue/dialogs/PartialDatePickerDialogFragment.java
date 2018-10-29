/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;

import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.RTE;

/**
 * Fragment wrapper for {@link PartialDatePickerDialog}
 *
 * @author pjw
 */
public class PartialDatePickerDialogFragment extends DialogFragment {
    private static final String BKEY_YEAR = "year";
    private static final String BKEY_MONTH = "month";
    private static final String BKEY_DAY = "day";

    private static final String BKEY_TITLE = "title";

    @StringRes
    private int mTitleId;
    @IdRes
    private int mDestinationFieldId;

    /** Currently displayed year; null if empty/invalid */
    @Nullable
    private Integer mYear;
    /** Currently displayed month; null if empty/invalid */
    @Nullable
    private Integer mMonth;
    /** Currently displayed day; null if empty/invalid */
    @Nullable
    private Integer mDay;

    /**
     * The callback received when the user "sets" the date in the dialog.
     * The event is passed on the the calling activity
     */
    private final PartialDatePickerDialog.OnDateSetListener mDialogListener = new PartialDatePickerDialog.OnDateSetListener() {
        public void onDateSet(@NonNull final PartialDatePickerDialog dialog,
                              final Integer year,
                              final Integer month,
                              final Integer day) {
            dialog.dismiss();
            ((OnPartialDatePickerResultListener) requireActivity()).onPartialDatePickerSet(PartialDatePickerDialogFragment.this,
                    mDestinationFieldId, year, month, day);
        }

        @Override
        public void onCancel(@NonNull final PartialDatePickerDialog dialog) {
            dialog.dismiss();
            ((OnPartialDatePickerResultListener) requireActivity()).onPartialDatePickerCancel(PartialDatePickerDialogFragment.this,
                    mDestinationFieldId);
        }
    };

    /**
     * Constructor
     *
     * @return new instance
     */
    public static PartialDatePickerDialogFragment newInstance() {
        return new PartialDatePickerDialogFragment();
    }

    /**
     * Check the activity supports the interface
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnPartialDatePickerResultListener)) {
            throw new RTE.MustImplementException(context, OnPartialDatePickerResultListener.class);
        }
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Restore saved state info
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BKEY_YEAR)) {
                mYear = savedInstanceState.getInt(BKEY_YEAR);
            }
            if (savedInstanceState.containsKey(BKEY_MONTH)) {
                mMonth = savedInstanceState.getInt(BKEY_MONTH);
            }
            if (savedInstanceState.containsKey(BKEY_DAY)) {
                mDay = savedInstanceState.getInt(BKEY_DAY);
            }
            mTitleId = savedInstanceState.getInt(BKEY_TITLE);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
        }

        // Create the dialog and listen (locally) for its events
        PartialDatePickerDialog editor = new PartialDatePickerDialog(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        editor.setDate(mYear, mMonth, mDay);
        editor.setOnDateSetListener(mDialogListener);
        return editor;
    }

    @NonNull
    public PartialDatePickerDialogFragment setDestinationFieldId(@IdRes final int id) {
        mDestinationFieldId = id;
        return this;
    }

    /**
     * Accessor. Update dialog if available.
     */
    @NonNull
    public PartialDatePickerDialogFragment setTitle(@StringRes final int title) {
        mTitleId = title;
        PartialDatePickerDialog d = (PartialDatePickerDialog) getDialog();
        if (d != null) {
            d.setTitle(mTitleId);
        }
        return this;
    }

    /**
     * @param current Current date (may be null)
     */
    @NonNull
    public PartialDatePickerDialogFragment setDate(@Nullable final Object current) {
        String dateString = current == null ? "" : current.toString();

        // get the current date
        Integer yyyy = null;
        Integer mm = null;
        Integer dd = null;
        try {
            String[] dateAndTime = dateString.split(" ");
            String[] date = dateAndTime[0].split("-");
            yyyy = Integer.parseInt(date[0]);
            mm = Integer.parseInt(date[1]);
            dd = Integer.parseInt(date[2]);
        } catch (Exception ignore) {
        }

        return setDate(yyyy, mm, dd);
    }

    /**
     * Accessor. Update dialog if available.
     */
    @NonNull
    private PartialDatePickerDialogFragment setDate(@Nullable final Integer year,
                                                    @Nullable final Integer month,
                                                    @Nullable final Integer day) {
        mYear = year;
        mMonth = month;
        mDay = day;
        PartialDatePickerDialog d = (PartialDatePickerDialog) getDialog();
        if (d != null) {
            d.setDate(mYear, mMonth, mDay);
        }
        return this;
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putInt(BKEY_TITLE, mTitleId);
        outState.putInt(UniqueId.BKEY_FIELD_ID, mDestinationFieldId);
        if (mYear != null) {
            outState.putInt(BKEY_YEAR, mYear);
        }
        if (mMonth != null) {
            outState.putInt(BKEY_MONTH, mMonth);
        }
        if (mDay != null) {
            outState.putInt(BKEY_DAY, mDay);
        }
        super.onSaveInstanceState(outState);
    }

    /**
     * Make sure data is saved in onPause() because onSaveInstanceState will have lost the views
     */
    @Override
    @CallSuper
    public void onPause() {
        PartialDatePickerDialog dialog = (PartialDatePickerDialog) getDialog();
        if (dialog != null) {
            mYear = dialog.getYear();
            mMonth = dialog.getMonth();
            mDay = dialog.getDay();
        }
        super.onPause();
    }



    /**
     * Listener interface to receive notifications when dialog is closed by any means.
     *
     * @author pjw
     */
    public interface OnPartialDatePickerResultListener {
        void onPartialDatePickerSet(@NonNull final PartialDatePickerDialogFragment dialog,
                                    @IdRes final int destinationFieldId,
                                    @Nullable final Integer year,
                                    @Nullable final Integer month,
                                    @Nullable final Integer day);

        void onPartialDatePickerCancel(@NonNull final PartialDatePickerDialogFragment dialog,
                                       @IdRes final int destinationFieldId);
    }
}
