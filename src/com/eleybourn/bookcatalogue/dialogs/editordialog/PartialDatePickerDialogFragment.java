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
package com.eleybourn.bookcatalogue.dialogs.editordialog;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Objects;

/**
 * Fragment wrapper for {@link PartialDatePickerDialog}
 *
 * @author pjw
 */
public class PartialDatePickerDialogFragment extends EditorDialogFragment {
    /** a standard sql style date string, must be correct */
    public static final String BKEY_DATE = "date";
    /** or the date split into components, which can partial */
    public static final String BKEY_YEAR = "year";
    public static final String BKEY_MONTH = "month";
    public static final String BKEY_DAY = "day";
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
                    ((OnPartialDatePickerResultsListener) getCallerFragment())
                            .onPartialDatePickerSave(PartialDatePickerDialogFragment.this,
                                    mDestinationFieldId, year, month, day);
                }

                @Override
                public void onPartialDatePickerCancel(final @NonNull PartialDatePickerDialog dialog) {
                    dialog.dismiss();
                    ((OnPartialDatePickerResultsListener) getCallerFragment())
                            .onPartialDatePickerCancel(PartialDatePickerDialogFragment.this,
                                    mDestinationFieldId);
                }
            };

    /** Currently displayed; null if empty/invalid */
    @Nullable
    private Integer mYear;
    @Nullable
    private Integer mMonth;
    @Nullable
    private Integer mDay;

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(final @Nullable Bundle savedInstanceState) {
        initStandardArgs(savedInstanceState);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BKEY_DATE)) {
                setDate(savedInstanceState.getString(BKEY_DATE, ""));
            } else {
                mYear = savedInstanceState.getInt(BKEY_YEAR);
                mMonth = savedInstanceState.getInt(BKEY_MONTH);
                mDay = savedInstanceState.getInt(BKEY_DAY);
            }
        } else {
            Bundle args = getArguments();
            Objects.requireNonNull(args);
            if (args.containsKey(BKEY_DATE)) {
                setDate(args.getString(BKEY_DATE, ""));
            } else {
                mYear = args.getInt(BKEY_YEAR);
                mMonth = args.getInt(BKEY_MONTH);
                mDay = args.getInt(BKEY_DAY);
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
