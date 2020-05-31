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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.utils.DateFormatUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * DialogFragment class to allow for selection of partial dates from 0AD to 9999AD.
 * <p>
 * Seems reasonable to disable relevant spinners if one is invalid, but it's actually
 * not very friendly when entering data for new books so we don't.
 * So for instance, if a day/month/year are set, and the user select "--" (unset) the month,
 * we leave the day setting unchanged.
 * A final validity check is done when trying to accept the date.
 */
public class PartialDatePickerDialogFragment
        extends BaseDatePickerDialogFragment {

    /** Log tag. */
    public static final String TAG = "PartialDatePickerDialog";

    /** Displayed to user: unset month. */
    private static final String UNKNOWN_MONTH = "---";
    /** Displayed to user: unset day. */
    private static final String UNKNOWN_DAY = "--";

    /** Used for reading month names + calculating number of days in a month. */
    private Calendar mCalendarForCalculations;
    private NumberPicker mDayPicker;
    /** This listener is called after <strong>any change</strong> made to the pickers. */
    private final NumberPicker.OnValueChangeListener mOnValueChangeListener =
            (picker, oldVal, newVal) -> {
                switch (picker.getId()) {
                    case R.id.year:
                        mYear = newVal;
                        // only February can be different number of days
                        if (mMonth != null && mMonth == 2) {
                            updateDaysInMonth();
                        }
                        break;

                    case R.id.month:
                        mMonth = newVal;
                        updateDaysInMonth();
                        break;

                    case R.id.day:
                        mDay = newVal;
                        break;

                    default:
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "id=" + picker.getId());
                        }
                        break;
                }
            };

    @StringRes
    private int mDialogTitleId;

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
            dateStr = DateFormatUtils.isoLocalDateForToday();
        } else if (currentValue != null) {
            dateStr = currentValue;
        } else {
            dateStr = "";
        }

        final DialogFragment frag = new PartialDatePickerDialogFragment();
        final Bundle args = new Bundle(2);
        args.putInt(StandardDialogs.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putString(BKEY_DATE, dateStr);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args != null) {
            mDialogTitleId = args.getInt(StandardDialogs.BKEY_DIALOG_TITLE);
        }

        //noinspection ConstantConditions
        mCalendarForCalculations = Calendar.getInstance(LocaleUtils.getUserLocale(getContext()));

        final Calendar calendar = Calendar.getInstance(LocaleUtils.getUserLocale(getContext()));

        setupDate(savedInstanceState);
        // can't have a null year. (but month/day can be null)
        // The user can/should use the "clear" button if they want no date at all.
        if (mYear == null) {
            mYear = calendar.get(Calendar.YEAR);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        final View root = getLayoutInflater().inflate(R.layout.dialog_partial_date_picker, null);

        // Ensure components match current Locale order
        reorderPickers(root);

        final NumberPicker yearPicker = root.findViewById(R.id.year);
        // we're optimistic...
        yearPicker.setMinValue(0);
        yearPicker.setMaxValue(2100);
        yearPicker.setOnValueChangedListener(mOnValueChangeListener);

        final NumberPicker monthPicker = root.findViewById(R.id.month);
        // 0: 'not set' + 1..12 real months
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(getMonthAbbr());
        monthPicker.setOnValueChangedListener(mOnValueChangeListener);

        mDayPicker = root.findViewById(R.id.day);
        // 0: 'not set'
        mDayPicker.setMinValue(0);
        // Make sure that the spinner can initially take any 'day' value. Otherwise,
        // when a dialog is reconstructed after rotation, the 'day' field will not be
        // restored by Android.
        mDayPicker.setMaxValue(31);
        mDayPicker.setFormatter(value -> value == 0 ? UNKNOWN_DAY : String.valueOf(value));
        mDayPicker.setOnValueChangedListener(mOnValueChangeListener);

        // set the initial date
        final int currentYear = mCalendarForCalculations.get(Calendar.YEAR);
        yearPicker.setValue(mYear != null ? mYear : currentYear);
        monthPicker.setValue(mMonth != null ? mMonth : 0);
        mDayPicker.setValue(mDay != null ? mDay : 0);
        updateDaysInMonth();

        //noinspection ConstantConditions
        return new MaterialAlertDialogBuilder(getContext())
                .setView(root)
                .setTitle(mDialogTitleId != 0 ? mDialogTitleId : R.string.action_edit)
                // no listeners. They must be set in the onResume of the fragment.
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.action_clear, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            // Set the dialog OnClickListener in onResume.
            // This allows us to validate the fields without
            // having the dialog close on us after the user clicks a button.
            dialog.getButton(Dialog.BUTTON_NEGATIVE).setOnClickListener(
                    v -> dismiss());
            dialog.getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener(
                    v -> send(null, null, null));
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (mDay != null && mDay > 0 && (mMonth == null || mMonth == 0)) {
                    Snackbar.make(v, R.string.warning_if_day_set_month_and_year_must_be,
                                  Snackbar.LENGTH_LONG).show();

                } else if (mMonth != null && mMonth > 0 && mYear == null) {
                    Snackbar.make(v, R.string.warning_if_month_set_year_must_be,
                                  Snackbar.LENGTH_LONG).show();

                } else {
                    send(mYear, mMonth, mDay);
                }
            });
        }
    }

    /**
     * Generate the month names (abbreviated). There are 13: first entry being 'unknown'.
     */
    private String[] getMonthAbbr() {
        // Set the day to 1 to avoid wrapping.
        mCalendarForCalculations.set(Calendar.DAY_OF_MONTH, 1);
        String[] monthNames = new String[13];
        monthNames[0] = UNKNOWN_MONTH;
        for (int i = 1; i <= 12; i++) {
            mCalendarForCalculations.set(Calendar.MONTH, i - 1);
            monthNames[i] = String.format("%tb", mCalendarForCalculations);
        }
        return monthNames;
    }

    /**
     * Depending on year/month selected, set the correct number of days.
     */
    private void updateDaysInMonth() {
        // Save the current day in case the regen alters it
        Integer daySave = mDay;

        // Determine the total days if we have a valid month/year
        int totalDays;
        if (mYear != null && mMonth != null && mMonth > 0) {
            mCalendarForCalculations.set(Calendar.YEAR, mYear);
            mCalendarForCalculations.set(Calendar.MONTH, mMonth - 1);
            totalDays = mCalendarForCalculations.getActualMaximum(Calendar.DAY_OF_MONTH);
        } else {
            // allow the user to start inputting with day first.
            totalDays = 31;
        }

        mDayPicker.setMaxValue(totalDays);

        // Ensure selected day is valid
        if (daySave == null || daySave == 0) {
            mDayPicker.setValue(0);
        } else {
            if (daySave > totalDays) {
                daySave = totalDays;
            }
            mDayPicker.setValue(daySave);
        }
    }

    /**
     * Reorder the views in the dialog to suit the current Locale.
     *
     * @param root Root view
     */
    private void reorderPickers(@NonNull final View root) {
        final char[] order;
        try {
            // This actually throws exception in some versions of Android, specifically when
            // the Locale specific date format has the day name (EEE) in it. So we exit and
            // just use our default order in these cases.
            // See BC Issue #712.
            order = DateFormat.getDateFormatOrder(getContext());
        } catch (@NonNull final RuntimeException e) {
            return;
        }

        // Default order is {year, month, day} so if that's the order then do nothing.
        if ((order[0] == 'y') && (order[1] == 'M')) {
            return;
        }

        // Remove the 3 pickers from their parent and add them back in the required order.
        final ViewGroup parent = root.findViewById(R.id.dateSelector);
        // Get the three views
        final ConstraintLayout y = parent.findViewById(R.id.yearSelector);
        final ConstraintLayout m = parent.findViewById(R.id.monthSelector);
        final ConstraintLayout d = parent.findViewById(R.id.daySelector);
        // Remove them
        parent.removeAllViews();
        // Re-add in the correct order.
        for (char c : order) {
            switch (c) {
                case 'd':
                    parent.addView(d);
                    break;
                case 'M':
                    parent.addView(m);
                    break;
                default:
                    parent.addView(y);
                    break;
            }
        }
    }
}
