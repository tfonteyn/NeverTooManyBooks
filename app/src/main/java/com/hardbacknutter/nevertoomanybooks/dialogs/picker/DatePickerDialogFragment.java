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
package com.hardbacknutter.nevertoomanybooks.dialogs.picker;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.DatePicker;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;

import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

public class DatePickerDialogFragment
        extends BaseDatePickerDialogFragment
        implements DatePickerDialog.OnDateSetListener {

    public static final String TAG = "DatePickerDialog";

    public static DialogFragment newInstance(@IdRes final int fieldId,
                                             @StringRes final int dialogTitleId,
                                             @NonNull final String currentValue,
                                             final boolean todayIfNone) {
        String date;
        if (todayIfNone && currentValue.isEmpty()) {
            date = DateUtils.localSqlDateForToday();
        } else {
            date = currentValue;
        }

        DatePickerDialogFragment frag = new DatePickerDialogFragment();
        Bundle args = new Bundle(3);
        args.putInt(UniqueId.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putInt(UniqueId.BKEY_FIELD_ID, fieldId);
        args.putString(BKEY_DATE, date);
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
        send(year, month + 1, day);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        baseSetup(savedInstanceState);

        //noinspection ConstantConditions
        Calendar calendar = Calendar.getInstance(LocaleUtils.getUserLocale(getContext()));

        // can't have null values, revert to today if needed.
        if (mYear == null) {
            mYear = calendar.get(Calendar.YEAR);
        }
        if (mMonth == null) {
            mMonth = calendar.get(Calendar.MONTH) + 1;
        }
        if (mDay == null) {
            mDay = calendar.get(Calendar.DAY_OF_MONTH);
        }
        return new DatePickerDialog(getContext(), this, mYear, mMonth - 1, mDay);
    }
}
