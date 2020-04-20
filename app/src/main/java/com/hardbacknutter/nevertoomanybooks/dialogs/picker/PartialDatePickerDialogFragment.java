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

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
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
        final String date;
        if (todayIfNone && (currentValue == null || currentValue.isEmpty())) {
            date = DateUtils.localSqlDateForToday();
        } else if (currentValue != null) {
            date = currentValue;
        } else {
            date = "";
        }

        final DialogFragment frag = new PartialDatePickerDialogFragment();
        final Bundle args = new Bundle(2);
        args.putInt(StandardDialogs.BKEY_DIALOG_TITLE, dialogTitleId);
        args.putString(BKEY_DATE, date);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        //noinspection ConstantConditions
        Calendar calendar = Calendar.getInstance(LocaleUtils.getUserLocale(getContext()));

        baseSetup(savedInstanceState);

        // can't have a null year. (but month/day can be null)
        // The user can/should use the "clear" button if they want no date at all.
        if (mYear == null) {
            mYear = calendar.get(Calendar.YEAR);
        }

        return new PartialDatePickerDialog(getContext(), getArguments());
    }

    /**
     * Set the dialog OnClickListener. This allows us to validate the fields without
     * having the dialog close on us after the user clicks a button.
     */
    @Override
    public void onResume() {
        super.onResume();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(Dialog.BUTTON_NEGATIVE).setOnClickListener(
                    v -> dismiss());
            dialog.getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener(
                    v -> send(null, null, null));
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (mDay != null && mDay > 0 && (mMonth == null || mMonth == 0)) {
                    //noinspection ConstantConditions
                    Snackbar.make(getDialog().getWindow().getDecorView(),
                                  R.string.warning_if_day_set_month_and_year_must_be,
                                  Snackbar.LENGTH_LONG).show();

                } else if (mMonth != null && mMonth > 0 && mYear == null) {
                    //noinspection ConstantConditions
                    Snackbar.make(getDialog().getWindow().getDecorView(),
                                  R.string.warning_if_month_set_year_must_be,
                                  Snackbar.LENGTH_LONG).show();

                } else {
                    send(mYear, mMonth, mDay);
                }
            });
        }
    }

    /**
     * Custom dialog. Button OnClickListener's must be set in the fragment onResume method.
     */
    class PartialDatePickerDialog
            extends AlertDialog {

        /** Used for reading month names + calculating number of days in a month. */
        @NonNull
        private final Calendar mCalendarForCalculations;
        @NonNull
        private final NumberPicker mDayPicker;
        /** This listener is called after <strong>any change</strong> made to the pickers. */
        @SuppressWarnings("FieldCanBeLocal")
        private final NumberPicker.OnValueChangeListener mOnValueChangeListener =
                (picker, oldVal, newVal) -> {
                    switch (picker.getId()) {
                        case R.id.PICKER_YEAR:
                            mYear = newVal;
                            // only February can be different number of days
                            if (mMonth != null && mMonth == 2) {
                                setDaysOfMonth();
                            }
                            break;

                        case R.id.PICKER_MONTH:
                            mMonth = newVal;
                            setDaysOfMonth();
                            break;

                        case R.id.PICKER_DAY:
                            mDay = newVal;
                            break;

                        default:
                            if (BuildConfig.DEBUG /* always */) {
                                Log.d(TAG, "id=" + picker.getId());
                            }
                            break;
                    }
                };

        /**
         * Constructor.
         *
         * <strong>Note:</strong> we explicitly pass in the inflater (independent from the
         * context) so we are 100% sure we're using the same one as in {@link #onCreateDialog}.
         * Call it paranoia.
         *
         * @param context Current context
         * @param args    optional arguments
         */
        PartialDatePickerDialog(@NonNull final Context context,
                                @Nullable final Bundle args) {
            super(context);

            mCalendarForCalculations = Calendar.getInstance(LocaleUtils.getUserLocale(context));
            int currentYear = mCalendarForCalculations.get(Calendar.YEAR);

            @SuppressLint("InflateParams")
            View root = LayoutInflater.from(context)
                                      .inflate(R.layout.dialog_partial_date_picker, null);

            // Ensure components match current Locale order
            reorderPickers(root);

            // Set the view
            setView(root);

            NumberPicker yearPicker = root.findViewById(R.id.year);
            yearPicker.setId(R.id.PICKER_YEAR);
            yearPicker.setMinValue(0);
            // we're optimistic...
            yearPicker.setMaxValue(2100);
            yearPicker.setOnValueChangedListener(mOnValueChangeListener);

            // Generate the month names.
            // Set the day to 1 to avoid wrapping.
            mCalendarForCalculations.set(Calendar.DAY_OF_MONTH, 1);
            // All month names (abbreviated). 13: first entry is 'unknown'
            String[] monthNames = new String[13];
            monthNames[0] = UNKNOWN_MONTH;
            for (int i = 1; i <= 12; i++) {
                mCalendarForCalculations.set(Calendar.MONTH, i - 1);
                monthNames[i] = String.format("%tb", mCalendarForCalculations);
            }
            NumberPicker monthPicker = root.findViewById(R.id.month);
            monthPicker.setId(R.id.PICKER_MONTH);
            monthPicker.setMinValue(0);
            // 12 months + the 'not set'
            monthPicker.setMaxValue(12);
            monthPicker.setDisplayedValues(monthNames);
            monthPicker.setOnValueChangedListener(mOnValueChangeListener);

            mDayPicker = root.findViewById(R.id.day);
            mDayPicker.setId(R.id.PICKER_DAY);
            mDayPicker.setMinValue(0);
            // Make sure that the spinner can initially take any 'day' value. Otherwise,
            // when a dialog is reconstructed after rotation, the 'day' field will not be
            // restored by Android.
            mDayPicker.setMaxValue(31);
            mDayPicker.setFormatter(value -> value == 0 ? UNKNOWN_DAY : String.valueOf(value));
            mDayPicker.setOnValueChangedListener(mOnValueChangeListener);

            // initial date
            yearPicker.setValue(mYear != null ? mYear : currentYear);
            monthPicker.setValue(mMonth != null ? mMonth : 0);
            mDayPicker.setValue(mDay != null ? mDay : 0);
            setDaysOfMonth();

            // no listeners. They must be set in the onResume of the fragment.
            setButton(DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                      (DialogInterface.OnClickListener) null);
            setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.action_clear),
                      (DialogInterface.OnClickListener) null);
            setButton(DialogInterface.BUTTON_POSITIVE, getString(android.R.string.ok),
                      (DialogInterface.OnClickListener) null);

            if (args != null) {
                @StringRes
                int titleId = args.getInt(StandardDialogs.BKEY_DIALOG_TITLE, R.string.action_edit);
                if (titleId != 0) {
                    setTitle(titleId);
                }
            }
        }

        /**
         * Depending on year/month selected, set the correct number of days.
         */
        private void setDaysOfMonth() {
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
            char[] order;
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
            ViewGroup parent = root.findViewById(R.id.dateSelector);
            // Get the three views
            ConstraintLayout y = parent.findViewById(R.id.yearSelector);
            ConstraintLayout m = parent.findViewById(R.id.monthSelector);
            ConstraintLayout d = parent.findViewById(R.id.daySelector);
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
}
