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
package com.hardbacknutter.nevertoomanybooks.dialogs.date;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.DatePicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;

public class DatePickerDialogFragment
        extends BaseDatePickerDialogFragment
        implements DatePickerDialog.OnDateSetListener {

    /** Log tag. */
    public static final String TAG = "DatePickerDialog";
    public static final String REQUEST_KEY = TAG + ":rk";

    /**
     * Constructor.
     *
     * @param dialogTitleId resource id for the dialog title
     * @param currentValue  the current value of the field
     * @param todayIfNone   {@code true} if we should use 'today' if the field was empty.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@StringRes final int dialogTitleId,
                                             @Nullable final String currentValue,
                                             final boolean todayIfNone) {
        final String dateStr;
        if (todayIfNone && (currentValue == null || currentValue.isEmpty())) {
            dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } else if (currentValue != null) {
            dateStr = currentValue;
        } else {
            dateStr = "";
        }

        final DialogFragment frag = new DatePickerDialogFragment();
        final Bundle args = new Bundle(2);
        args.putInt(StandardDialogs.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putString(BKEY_DATE, dateStr);
        frag.setArguments(args);
        return frag;
    }

    /**
     * This listener is only called when the user has confirmed the selected date.
     *
     * @param month 0..11 based
     */
    @Override
    public void onDateSet(@NonNull final DatePicker view,
                          final int year,
                          final int month,
                          final int day) {
        // forward it to our own listener
        DatePickerResultsListener.sendResult(this, REQUEST_KEY, year, month + 1, day);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        setupDate(savedInstanceState);

        // can't have {@code 0} values here, revert to today if needed.
        final LocalDate now = LocalDate.now();
        if (mYear == 0) {
            mYear = now.getYear();
        }
        if (mMonth == 0) {
            mMonth = now.getMonthValue();
        }
        if (mDay == 0) {
            mDay = now.getDayOfMonth();
        }
        //noinspection ConstantConditions
        return new DatePickerDialog(getContext(), this, mYear, mMonth - 1, mDay);
    }
}
