/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

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
        extends BaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "PartialDatePickerDialog";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    /** a standard sql style date string, must be correct. */
    private static final String BKEY_DATE = TAG + ":date";
    /** Argument. */
    private static final String BKEY_FIELD_ID = TAG + ":fieldId";
    private static final String BKEY_DIALOG_TITLE = TAG + ":title";

    /** Displayed to user: unset month. */
    private static final String UNKNOWN_MONTH = "---";
    /** Displayed to user: unset day. */
    private static final String UNKNOWN_DAY = "--";
    /** or the date split into components, which can be partial. */
    private static final String SIS_YEAR = TAG + ":year";
    /** range: 0: 'not set' or 1..12. */
    private static final String SIS_MONTH = TAG + ":month";
    /** range: 0: 'not set' or 1..31. */
    private static final String SIS_BKEY_DAY = TAG + ":day";
    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;
    @IdRes
    private int mFieldId;

    /** Currently displayed; {@code 0} if empty/invalid. */
    private int mYear;
    /**
     * Currently displayed; {@code 0} if invalid/empty.
     * <strong>IMPORTANT:</strong> 1..12 based. (the jdk internals expect 0..11).
     */
    private int mMonth;
    /** Currently displayed; {@code 0} if empty/invalid. */
    private int mDay;

    private NumberPicker mDayPicker;

    /** This listener is called after <strong>any change</strong> made to the pickers. */
    private final NumberPicker.OnValueChangeListener mOnValueChangeListener =
            (picker, oldVal, newVal) -> {
                final int pickerId = picker.getId();

                if (pickerId == R.id.year) {
                    mYear = newVal;
                    // only February can be different number of days
                    if (mMonth == 2) {
                        updateDaysInMonth();
                    }

                } else if (pickerId == R.id.month) {
                    mMonth = newVal;
                    updateDaysInMonth();

                } else if (pickerId == R.id.day) {
                    mDay = newVal;

                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "id=" + picker.getId());
                    }
                }
            };

    @StringRes
    private int mDialogTitleId;

    /**
     * No-arg constructor for OS use.
     */
    public PartialDatePickerDialogFragment() {
        super(R.layout.dialog_partial_date_picker);
    }


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                             "BKEY_REQUEST_KEY");
        mDialogTitleId = args.getInt(BKEY_DIALOG_TITLE);
        mFieldId = args.getInt(BKEY_FIELD_ID);

        if (savedInstanceState == null) {
            // BKEY_DATE is only present in the original args
            parseDate(args.getString(BKEY_DATE));
        } else {
            // These are only present in the savedInstanceState
            mYear = savedInstanceState.getInt(SIS_YEAR);
            mMonth = savedInstanceState.getInt(SIS_MONTH);
            mDay = savedInstanceState.getInt(SIS_BKEY_DAY);
        }

        // can't have a 0 year. (but month/day can be 0)
        // The user can/should use the "clear" button if they want no date at all.
        if (mYear == 0) {
            mYear = LocalDate.now().getYear();
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Ensure components match current Locale order
        reorderPickers(view);

        final Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(mDialogTitleId != 0 ? mDialogTitleId : R.string.action_edit);

        final NumberPicker yearPicker = view.findViewById(R.id.year);
        // 0: 'not set'
        yearPicker.setMinValue(0);
        // we're optimistic...
        yearPicker.setMaxValue(2100);
        yearPicker.setOnValueChangedListener(mOnValueChangeListener);

        final NumberPicker monthPicker = view.findViewById(R.id.month);
        // 0: 'not set' + 1..12 real months
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(getMonthAbbr());
        monthPicker.setOnValueChangedListener(mOnValueChangeListener);

        mDayPicker = view.findViewById(R.id.day);
        // 0: 'not set'
        mDayPicker.setMinValue(0);
        // Make sure that the spinner can initially take any 'day' value. Otherwise,
        // when a dialog is reconstructed after rotation, the 'day' field will not be
        // restored by Android.
        mDayPicker.setMaxValue(31);
        mDayPicker.setFormatter(value -> value == 0 ? UNKNOWN_DAY : String.valueOf(value));
        mDayPicker.setOnValueChangedListener(mOnValueChangeListener);

        // set the initial date
        yearPicker.setValue(mYear != 0 ? mYear : LocalDate.now().getYear());
        monthPicker.setValue(mMonth);
        mDayPicker.setValue(mDay);
        updateDaysInMonth();
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
        }
        return false;
    }

    private boolean saveChanges() {
        if (mDay != 0 && mMonth == 0) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_if_day_set_month_and_year_must_be,
                          Snackbar.LENGTH_LONG).show();

        } else if (mMonth != 0 && mYear == 0) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_if_month_set_year_must_be,
                          Snackbar.LENGTH_LONG).show();

        } else {
            Launcher.sendResult(this, mRequestKey, mFieldId, mYear, mMonth, mDay);
            return true;
        }

        return false;
    }


    /**
     * Generate the month names (abbreviated). There are 13: first entry being 'unknown'.
     */
    private String[] getMonthAbbr() {
        //noinspection ConstantConditions
        final Locale userLocale = AppLocale.getInstance().getUserLocale(getContext());
        final String[] monthNames = new String[13];
        monthNames[0] = UNKNOWN_MONTH;
        for (int i = 1; i <= 12; i++) {
            monthNames[i] = Month.of(i).getDisplayName(TextStyle.SHORT, userLocale);
        }
        return monthNames;
    }

    /**
     * Depending on year/month selected, set the correct number of days.
     */
    private void updateDaysInMonth() {
        int currentlySelectedDay = mDay;

        // Determine the total days if we have a valid month/year
        final int totalDays;
        if (mYear != 0 && mMonth != 0) {
            totalDays = LocalDate.of(mYear, mMonth, 1).lengthOfMonth();
        } else {
            // allow the user to start inputting with day first.
            totalDays = 31;
        }

        mDayPicker.setMaxValue(totalDays);

        // Ensure selected day is valid
        if (currentlySelectedDay == 0) {
            mDayPicker.setValue(0);
        } else {
            if (currentlySelectedDay > totalDays) {
                currentlySelectedDay = totalDays;
            }
            mDayPicker.setValue(currentlySelectedDay);
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

        // Default order is {year, month, day} so if that's the order do nothing.
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
        for (final char c : order) {
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

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SIS_YEAR, mYear);
        outState.putInt(SIS_MONTH, mMonth);
        outState.putInt(SIS_BKEY_DAY, mDay);
    }

    /**
     * Parse the input ISO date string into the individual components.
     * <p>
     * Note we don't use {@link DateParser}
     * as we the current implementation always returns full dates.
     * Here, we explicitly need to support partial dates.
     *
     * <ul>Allowed formats:
     *      <li>yyyy-mm-dd time</li>
     *      <li>yyyy-mm-dd</li>
     *      <li>yyyy-mm</li>
     *      <li>yyyy</li>
     * </ul>
     *
     * @param dateString SQL formatted (partial) date, can be {@code null}.
     */
    private void parseDate(@Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            mYear = 0;
            mMonth = 0;
            mDay = 0;
            return;
        }

        int yyyy = 0;
        int mm = 0;
        int dd = 0;
        try {
            final String[] dateAndTime = dateString.split(" ");
            final String[] date = dateAndTime[0].split("-");
            yyyy = Integer.parseInt(date[0]);
            if (date.length > 1) {
                mm = Integer.parseInt(date[1]);
            }
            if (date.length > 2) {
                dd = Integer.parseInt(date[2]);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore. Any values we did get, are used.
        }

        mYear = yyyy;
        mMonth = mm;
        mDay = dd;
    }

    public abstract static class Launcher
            extends DialogFragmentLauncherBase {

        private static final String FIELD_ID = "fieldId";
        private static final String YEAR = "year";
        private static final String MONTH = "month";
        private static final String DAY = "day";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               @IdRes final int fieldId,
                               final int year,
                               final int month,
                               final int day) {
            final Bundle result = new Bundle(4);
            result.putInt(FIELD_ID, fieldId);
            result.putInt(YEAR, year);
            result.putInt(MONTH, month);
            result.putInt(DAY, day);

            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param dialogTitleId resource id for the dialog title
         * @param fieldId       this dialog operates on
         *                      (one launcher can serve multiple fields)
         * @param currentValue  the current value of the field
         * @param todayIfNone   {@code true} if we should use 'today' if the field was empty.
         */
        public void launch(@StringRes final int dialogTitleId,
                           @IdRes final int fieldId,
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

            final Bundle args = new Bundle(4);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putInt(BKEY_DIALOG_TITLE, dialogTitleId);
            args.putInt(BKEY_FIELD_ID, fieldId);
            args.putString(BKEY_DATE, dateStr);

            final DialogFragment frag = new PartialDatePickerDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, PartialDatePickerDialogFragment.TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {

            onResult(result.getInt(FIELD_ID),
                     new PartialDate(result.getInt(YEAR),
                                     result.getInt(MONTH),
                                     result.getInt(DAY)));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param fieldId this destination field id
         * @param date    the picked date
         */
        public abstract void onResult(@IdRes int fieldId,
                                      @NonNull PartialDate date);
    }
}
