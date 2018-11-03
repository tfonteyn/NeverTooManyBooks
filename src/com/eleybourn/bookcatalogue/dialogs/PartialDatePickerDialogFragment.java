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

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.utils.RTE;

import java.util.Objects;

/**
 * Fragment wrapper for {@link PartialDatePickerDialog}
 *
 * @author pjw
 */
public class PartialDatePickerDialogFragment extends DialogFragment {
    /** a standard sql style date string, must be correct */
    public static final String BKEY_DATE = "date";
    /** or the date split into components, which can partial */
    public static final String BKEY_YEAR = "year";
    public static final String BKEY_MONTH = "month";
    public static final String BKEY_DAY = "day";

    @StringRes
    private int mTitleId;
    @IdRes
    private int mDestinationFieldId;

    /**
     * Object to handle changes
     */
    private final PartialDatePickerDialog.OnPartialDatePickerResultsListener mDialogListener =
            new PartialDatePickerDialog.OnPartialDatePickerResultsListener() {
                public void onPartialDatePickerSave(final @NonNull PartialDatePickerDialog dialog,
                                                    final Integer year,
                                                    final Integer month,
                                                    final Integer day) {
                    dialog.dismiss();
                    ((OnPartialDatePickerResultsListener) requireActivity()).onPartialDatePickerSave(PartialDatePickerDialogFragment.this,
                            mDestinationFieldId, year, month, day);
                }

                @Override
                public void onPartialDatePickerCancel(final @NonNull PartialDatePickerDialog dialog) {
                    dialog.dismiss();

                    ((OnPartialDatePickerResultsListener) requireActivity()).onPartialDatePickerCancel(PartialDatePickerDialogFragment.this,
                            mDestinationFieldId);
                }
            };

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
     * Check the activity supports the interface
     */
    @Override
    @CallSuper
    public void onAttach(final @NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof OnPartialDatePickerResultsListener)) {
            throw new RTE.MustImplementException(context, OnPartialDatePickerResultsListener.class);
        }
    }

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mTitleId = savedInstanceState.getInt(UniqueId.BKEY_DIALOG_TITLE);
            mDestinationFieldId = savedInstanceState.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
            if (savedInstanceState.containsKey(BKEY_DATE)) {
                setDate(savedInstanceState.getString(BKEY_DATE, ""));
            } else {
                if (savedInstanceState.containsKey(BKEY_YEAR)) {
                    mYear = savedInstanceState.getInt(BKEY_YEAR);
                }
                if (savedInstanceState.containsKey(BKEY_MONTH)) {
                    mMonth = savedInstanceState.getInt(BKEY_MONTH);
                }
                if (savedInstanceState.containsKey(BKEY_DAY)) {
                    mDay = savedInstanceState.getInt(BKEY_DAY);
                }
            }
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);
            mTitleId = args.getInt(UniqueId.BKEY_DIALOG_TITLE, R.string.edit);
            mDestinationFieldId = args.getInt(UniqueId.BKEY_FIELD_ID);
            // data to edit
            if (args.containsKey(BKEY_DATE)) {
                setDate(args.getString(BKEY_DATE, ""));
            } else {
                if (args.containsKey(BKEY_YEAR)) {
                    mYear = args.getInt(BKEY_YEAR);
                }
                if (args.containsKey(BKEY_MONTH)) {
                    mMonth = args.getInt(BKEY_MONTH);
                }
                if (args.containsKey(BKEY_DAY)) {
                    mDay = args.getInt(BKEY_DAY);
                }
            }
        }

        // Create the dialog and listen (locally) for its events
        PartialDatePickerDialog editor = new PartialDatePickerDialog(requireActivity());
        if (mTitleId != 0) {
            editor.setTitle(mTitleId);
        }
        editor.setDate(mYear, mMonth, mDay);
        editor.setResultsListener(mDialogListener);
        return editor;
    }

    /**
     * @param dateString SQL formatted date, may be null
     */
    private void setDate(final @NonNull String dateString) {
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

        setDate(yyyy, mm, dd);
    }

    private void setDate(final @Nullable Integer year,
                         final @Nullable Integer month,
                         final @Nullable Integer day) {
        mYear = year;
        mMonth = month;
        mDay = day;
        PartialDatePickerDialog d = (PartialDatePickerDialog) getDialog();
        if (d != null) {
            d.setDate(mYear, mMonth, mDay);
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(final @NonNull Bundle outState) {
        outState.putInt(UniqueId.BKEY_DIALOG_TITLE, mTitleId);
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
    public interface OnPartialDatePickerResultsListener {
        void onPartialDatePickerSave(final @NonNull PartialDatePickerDialogFragment dialog,
                                     final @IdRes int destinationFieldId,
                                     final @Nullable Integer year,
                                     final @Nullable Integer month,
                                     final @Nullable Integer day);

        void onPartialDatePickerCancel(final @NonNull PartialDatePickerDialogFragment dialog,
                                       final @IdRes int destinationFieldId);
    }
}
